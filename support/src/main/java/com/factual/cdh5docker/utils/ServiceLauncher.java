package com.factual.cdh5docker.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.protocol.HdfsConstants.DatanodeReportType;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Shell.ExitCodeException;
import org.apache.hadoop.util.Shell.ShellCommandExecutor;
import org.apache.hadoop.util.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.common.collect.Maps;

/**
 * ServiceLauncher - Utility used to launch ZOOKEEPER, HDFS, YARN, HBASE and KAFA services in a *Test* environment.  
 * 
 * @author rana
 *
 */
public class ServiceLauncher {

  
  public static class LaunchResult {
    // a ready latch that can be WAITed on until all services are in a steady state - will also trigger in failed state  
    CountDownLatch readyLatch;
    // launch thread handle - will signal when all services are shutdown (either by user or due to fault). 
    Thread launchThread=null;
    // boolean indicating successful launch - this should be checked after readyLatch has triggered. 
    AtomicBoolean launchStatus = new AtomicBoolean(false);
    // exception reference (if there was an error)
    AtomicReference<Exception> lastException = new AtomicReference<Exception>();
  }
  
  public static class LaunchConfig {
    
    public LaunchConfig() { 

    }
    
    // we need a total worker thread count 
    int threadCount;
    // we use this semaphore to track completed worker threads   
    final Semaphore completedThreadSemaphore = new Semaphore(0);
    // ready latch triggered when all services are in a stable state 
    public final CountDownLatch readyLatch = new CountDownLatch(1);
    // shutdown latch .. used to indicate / trigger shutdown 
    public final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public static final int DEFAULT_PROCESS_MEMORY_MB = 256;
    // NN (HDFS) Memory in MB 
    public int NNMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    // DN (HDFS) Memory in MB 
    public int DNMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    // RM (YARN) Memroy in MB 
    public int RMMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    // NM (YARN) Memroy in MB 
    public int NMMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    // ZK Memory in MB 
    public int ZMMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    // HBASE MASTER Memory MB 
    public int HBaseMasterMB = DEFAULT_PROCESS_MEMORY_MB;
    // HBASE REGION Server Memory MB 
    public int HBaseRegionServerMB = DEFAULT_PROCESS_MEMORY_MB * 2;
    // KAFKA Memory MB 
    public int KafkaMemoryMB = DEFAULT_PROCESS_MEMORY_MB;
    
  }
  
  
  /**
   *  Launch ZOOKEEPER, HDFS, YARN, HBASE and KAFA services in a test context.
   *  
   * @param launchConfig - optional memory parameters to use with each service type and a shutdown semaphore that can be used for early termination 
   * 
   * @return LaunchResult - 
   */
  public static LaunchResult launchServices(final LaunchConfig launchConfig) { 
    
    
    final LaunchResult result = new LaunchResult();
    result.readyLatch = launchConfig.readyLatch;
    
    result.launchThread = new Thread(new Runnable() {

      @Override
      public void run() {
        
        // true indicates success 
        result.launchStatus.set(false);
        
        try { 
          // kill everything upfront ... 
          killServices();
          
          if (launchHDFS(Role.FORMAT,launchConfig) != 0) { 
            LOG.error("Failed to FORMAT HDFS");
          }
          else { 
            CountDownLatch loginLatch = new CountDownLatch(1);
            CountDownLatch hbaseReadyLatch = new CountDownLatch(1);

            startLoginThread(launchConfig,loginLatch);
            
            AtomicInteger exitCodes[] = new AtomicInteger[2];
            for (int i=0;i<2;++i) 
              exitCodes[i]=new AtomicInteger();
            
            launchSecureZookeeper(launchConfig);
            launchHDFSThread(Role.NAMENODE, launchConfig, exitCodes[0]);
            launchHDFSThread(Role.DATANODE, launchConfig, exitCodes[1]);
            launchSecureHBase(HBASEROLE.REGIONSERVER,launchConfig,hbaseReadyLatch);
            launchSecureHBase(HBASEROLE.MASTER,launchConfig,hbaseReadyLatch);
            launchHBaseReadyStateThread(launchConfig,hbaseReadyLatch);

            long loginWaitTimeStart = System.currentTimeMillis();
            LOG.info("Waiting for login to complete");
            try {
              loginLatch.await();
            } catch (InterruptedException e1) {
              
            }
            
            long loginWaitTimeEnd = System.currentTimeMillis();
            LOG.info("login completed in:" + (loginWaitTimeEnd-loginWaitTimeStart));
           
            waitForHDFSToBeReady();

            
            try {
              DirectoryUtils.createServiceDirs();
            } catch (IOException e) {
              LOG.info(StringUtils.stringifyException(e));
            }
            
            launchKafka(launchConfig);
            launchSecureYARN(YARNROLE.RESOURCEMANAGER,launchConfig);
            launchSecureYARN(YARNROLE.NODEMANAGER,launchConfig);
            
            LOG.info("Waiting for hbase to get in ready state");
            try {
              hbaseReadyLatch.await();
            } catch (InterruptedException e) {
            }
            LOG.info("hbase in ready state");
            
            result.launchStatus.set(true);

            // ok we release the the ready latch here
            launchConfig.readyLatch.countDown();

            LOG.info("Waiting for someone to trigger exit");
            launchConfig.shutdownLatch.await();
            LOG.info("Exit triggered. Leaving main thread.");
            
          }
        }
        catch (Exception e) {
          result.lastException.set(e);
          LOG.error("Exception in Main Thread:" + StringUtils.stringifyException(e));
        }
        finally { 
          // no matter what, before exiting trigger ready latch 
          // it may have been triggered above, in which case this is a noop.
          launchConfig.readyLatch.countDown();
          LOG.info("Exiting ... Killing all services");
          // kill all services upon exit 
          killServices();
          // wait for all threads to die 
          
          int remainingThreads = launchConfig.threadCount;
          while (remainingThreads != 0) {
            LOG.info("Waiting for " + remainingThreads + " threads to die" );
            launchConfig.completedThreadSemaphore.acquireUninterruptibly();
            remainingThreads--;
          }
          LOG.info("All worker threads dead. Exiting for good");
        }
      } 
      
    });
    result.launchThread.start();
    return result;
  }  
  
  private static final String COMMAND_DN_MEMORY = "dnMem";
  private static final String COMMAND_NN_MEMORY = "nnMem";
  private static final String COMMAND_FORMAT_HDFS = "format";
    
  enum Role {
    FORMAT,
    NAMENODE,
    DATANODE
  }
  
  static Logger LOG = Logger.getLogger(ServiceLauncher.class); 
  
  private static void removeCryptographyRestrictions() {
    if (!isRestrictedCryptography()) {
      LOG.trace("Cryptography restrictions removal not needed");
      return;
    }
    try {
        /*
         * Do the following, but with reflection to bypass access checks:
         *
         * JceSecurity.isRestricted = false;
         * JceSecurity.defaultPolicy.perms.clear();
         * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
         */
        final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
        final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
        final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

        final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
        isRestrictedField.setAccessible(true);
        isRestrictedField.set(null, false);

        final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
        defaultPolicyField.setAccessible(true);
        final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

        final Field perms = cryptoPermissions.getDeclaredField("perms");
        perms.setAccessible(true);
        ((Map<?, ?>) perms.get(defaultPolicy)).clear();

        final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        defaultPolicy.add((Permission) instance.get(null));

        LOG.trace("Successfully removed cryptography restrictions");
    } catch (final Exception e) {
        LOG.warn("Failed to remove cryptography restrictions", e);
    }
}

private static boolean isRestrictedCryptography() {
    // This simply matches the Oracle JRE, but not OpenJDK.
    return "Java(TM) SE Runtime Environment".equals(System.getProperty("java.runtime.name"));
}
  
  private static void printHelpMessage(Options options) {
    System.out.println("HDFSLauncher Utility.");
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    formatter.printHelp("general options are:", options);
  }

  static void launchKafka(final LaunchConfig launchConfig) {
    launchConfig.threadCount++;
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          
          List<String> command = new ArrayList<String>();
          
          String logFile = "/var/log/kafka.log";    
          command.addAll(Arrays.asList("/usr/bin/runAs.sh","kafka",logFile,"export KAFKA_HEAP_OPTS=-Xmx" + launchConfig.KafkaMemoryMB + "m && /usr/local/lib/kafka/bin/kafka-server-start.sh /usr/local/lib/kafka/config/server.properties"));
          String[] commandArray = command.toArray(new String[command.size()]);
          
          ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray);
          
          LOG.info("Launching Kafka with Command:" + command);
          try {
            LOG.info(Arrays.toString(shExec.getExecString()));
            shExec.execute();
          } catch (ExitCodeException e) {
            LOG.info(e.toString());
          }
        }
        catch (IOException e) { 
          LOG.info("Kafka Server Launch Threw:" + StringUtils.stringifyException(e));
        }
        finally { 
          LOG.info("Kafka Server Exited");
          launchConfig.shutdownLatch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }        
      }
    });
    thread.start();
    
  }
  
  
  static void launchSecureZookeeper(final LaunchConfig launchConfig) {
    
    launchConfig.threadCount++;
    
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          {
            List<String> command = new ArrayList<String>();
            
            String logFile = "/dev/null";    
            command.addAll(Arrays.asList("/usr/bin/runAs.sh","zookeeper",logFile,"/usr/bin/zookeeper-server-initialize --force"));
            String[] commandArray = command.toArray(new String[command.size()]);
            
            ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray);
            
            LOG.info("Formatting Zookeeper with Command:" + command);
            try {
              LOG.info(Arrays.toString(shExec.getExecString()));
              shExec.execute();
            } catch (ExitCodeException e) {
              // TODO Auto-generated catch block
              LOG.info(e.toString());
            }
            
          }
          {
            List<String> command = new ArrayList<String>();
            
            String logFile = "/dev/null";    
            command.addAll(Arrays.asList("/usr/bin/runAs.sh","zookeeper",logFile,"/usr/bin/zookeeper-server start-foreground"));
            String[] commandArray = command.toArray(new String[command.size()]);
            
            ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray);
            
            LOG.info("Launching Zookeeper with Command:" + command);
            try {
              LOG.info(Arrays.toString(shExec.getExecString()));
              shExec.execute();
            } catch (ExitCodeException e) {
              // TODO Auto-generated catch block
              LOG.info(e.toString());
            }
          }
        }
        catch (IOException e) { 
          LOG.info("Zookeeper Server Launch Threw:" + StringUtils.stringifyException(e));
        }
        finally { 
          LOG.info("Zookeeper Server Exited");
          launchConfig.shutdownLatch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();

        }
      } 
      
    });
    thread.start();
  }
  
  enum YARNROLE { 
    RESOURCEMANAGER,
    NODEMANAGER
  }
  
  static void launchSecureYARN(final YARNROLE role, final LaunchConfig launchConfig) {
    launchConfig.threadCount++;
    
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          
          List<String> commandList = new ArrayList<String>();
          
          String logFile;
          String command;
          String heap;
          if (role == YARNROLE.RESOURCEMANAGER) { 
            logFile = "/var/log/hadoop-yarn/yarn-resourcemanager.log";
            command = "resourcemanager";
            heap = "export YARN_RESOURCEMANAGER_HEAPSIZE=" + launchConfig.RMMemoryMB +" && ";
          }
          else { 
            logFile = "/var/log/hadoop-yarn/yarn-nodemanager.log";
            command = "nodemanager";
            heap = "export YARN_NODEMANAGER_HEAPSIZE=" + launchConfig.NMMemoryMB +" && ";
          }
          
          commandList.addAll(Arrays.asList("/usr/bin/runAs.sh","yarn",logFile,
              heap + "cd /usr/lib/hadoop-yarn && export HADOOP_LIBEXEC_DIR=/usr/lib/hadoop/libexec && /usr/lib/hadoop-yarn/bin/yarn --config /etc/hadoop/conf/ "+ command));
          
          ShellCommandExecutor shExec = new ShellCommandExecutor(commandList.toArray(new String[commandList.size()]));
          
          LOG.info("Launching YARN(" + role+") with Command:" + command);
          try {
            LOG.info(Arrays.toString(shExec.getExecString()));
            shExec.execute();
          } catch (ExitCodeException e) {
            // TODO Auto-generated catch block
            LOG.info(e.toString());
          }
        }
        catch (IOException e) { 
          LOG.info("YARN(" + role+") Launch Threw:" + StringUtils.stringifyException(e));
        }
        finally { 
          LOG.info("YARN(" + role+") Exited");
          launchConfig.shutdownLatch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }
      } 
      
    });
    thread.start();
  
  }
  
  
  enum HBASEROLE { 
    MASTER,
    REGIONSERVER
  }
  
  
  static void launchSecureHBase(final HBASEROLE role, final LaunchConfig launchConfig,final CountDownLatch hbaseReadyLatch) {
    
    launchConfig.threadCount++;
    
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          
          List<String> commandList = new ArrayList<String>();
          
          String logFile;
          String command;
          int heapSize=-1;
          
          if (role == HBASEROLE.MASTER) { 
            logFile = "/var/log/hbase/hbase-master.log";
            command = "master";
            heapSize = launchConfig.HBaseMasterMB;
          }
          else { 
            logFile = "/var/log/hbase/hbase-regionserver.log";
            command = "regionserver";
            heapSize = launchConfig.HBaseRegionServerMB;
          }
          
          commandList.addAll(Arrays.asList("/usr/bin/runAs.sh","hbase",logFile,"export HBASE_HEAPSIZE=" + heapSize +" && /usr/lib/hbase/bin/hbase --config /etc/hbase/conf/ " + command + " start"));
          
          ShellCommandExecutor shExec = new ShellCommandExecutor(commandList.toArray(new String[commandList.size()]));
          
          LOG.info("Launching HBASE(" + role+") with Command:" + command);
          try {
            LOG.info(Arrays.toString(shExec.getExecString()));
            shExec.execute();
          } catch (ExitCodeException e) {
            // TODO Auto-generated catch block
            LOG.info(e.toString());
          }
        }
        catch (IOException e) { 
          LOG.info("HBASE(" + role+") Launch Threw:" + StringUtils.stringifyException(e));
        }
        finally { 
          LOG.info("HBASE(" + role+") Exited");
          launchConfig.shutdownLatch.countDown();
          // also count down ready latch since a dead hbase will never
          // be ready :-(
          hbaseReadyLatch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }
      } 
      
    });
    thread.start();
  }
  
  static int launchHDFS(final Role role, final LaunchConfig launchConfig) {
    
    final AtomicInteger exitCode = new AtomicInteger(-1);

    if (role == Role.FORMAT) { 
      FileUtil.fullyDelete(new File("/var/lib/hadoop-hdfs/cache/hdfs/dfs/data"));
      FileUtil.fullyDelete(new File("/var/lib/hadoop-hdfs/cache/hdfs/dfs/name"));
    }
    
    List<String> command = new ArrayList<String>();
    
    String logFile = "/var/log/hadoop-hdfs/";
    String daemonName=null;
    
    int memoryMB = -1;
    if (role == Role.NAMENODE || role == Role.FORMAT){
      logFile += "hadoop-namenode.log";
      daemonName = "namenode";
      if (role == Role.FORMAT) { 
        daemonName += " -format -force";
      }
      memoryMB = launchConfig.NMMemoryMB;
    }
    else {  
      logFile += "hadoop-datanode.log";
      daemonName = "datanode";
      memoryMB = launchConfig.DNMemoryMB;
    }
    
    command.addAll(Arrays.asList("/usr/bin/runAs.sh","hdfs",logFile,"hdfs --config /etc/hadoop/conf " + daemonName));
    String[] commandArray = command.toArray(new String[command.size()]);
    
    Map<String,String> env = Maps.newHashMap();
    
    if (memoryMB != -1) { 
      env.put("HADOOP_HEAPSIZE", Integer.toString(memoryMB));
    }
    
    ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray,null,env);
    
    LOG.info("Launching " + role+ " with Command:" + command);
    try {
      LOG.info(Arrays.toString(shExec.getExecString()));
      shExec.execute();
      exitCode.set(0);
    } catch (ExitCodeException e) {
      // TODO Auto-generated catch block
      LOG.info(e.toString());
      exitCode.set(shExec.getExitCode());
    }
    catch (IOException e) { 
      exitCode.set(-1);
    }
    LOG.info(role + " Exited with exitCode:" + exitCode.get());
    
    return exitCode.get();
    
  }
  
  static void launchHDFSThread(final Role role,final LaunchConfig launchConfig, final AtomicInteger returnCode) { 
    launchConfig.threadCount++;
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        returnCode.set(-1);
        try { 
          returnCode.set(launchHDFS(role, launchConfig));
        }
        finally { 
          launchConfig.shutdownLatch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }
      } 
      
    });
    thread.start();
  }
  
  
  static void killServices() { 
      List<String> command = new ArrayList<String>();
      
      command.addAll(Arrays.asList("/usr/bin/killServices.sh"));
      String[] commandArray = command.toArray(new String[command.size()]);
      
      ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray);
      
      LOG.info("Kill all Services with Command:" + command);
      try {
        LOG.info(Arrays.toString(shExec.getExecString()));
        shExec.execute();
      } catch (Exception e) {
      }
    }
  
  static void waitForZookeeperToBeReady() throws InterruptedException {
      List<String> command = new ArrayList<String>();
      command.addAll(Arrays.asList("/usr/lib/zookeeper/bin/zkServer.sh", "status"));
      String[] commandArray = command.toArray(new String[command.size()]);
      ShellCommandExecutor shExec = new ShellCommandExecutor(commandArray);
      boolean triedOnce = false;

      LOG.info("Waiting for Zookeeper to be ready");

      while (! triedOnce || shExec.getExitCode() != 0) {
          try {
              if (!triedOnce) triedOnce = true;
              shExec.execute();
          } catch (ExitCodeException e) {
              LOG.info("Still waiting for zookeeper to be ready");
              Thread.sleep(3000);
          } catch (IOException e) {
              e.printStackTrace();
          }       
      }

      LOG.info("Zookeeper is ready");
  }
  
  /** Wait until the given namenode gets registration from all the datanodes */
  static void waitForHDFSToBeReady() throws IOException {
    
    long timeStart = System.currentTimeMillis();
    
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);
    DFSClient client = new DFSClient(fs.getUri(),conf);

    try { 
      // ensure all datanodes have registered and sent heartbeat to the namenode
      while (true) {
        try { 
          client.datanodeReport(DatanodeReportType.LIVE);
          break;
        }
        catch (ConnectException e) { 
          //LOG.info("Failed to Connect to Namenode");
        }
        try {
          //LOG.info("Waiting for cluster to become active");
          Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        
      }
    }
    finally { 
      client.close();
      long timeEnd = System.currentTimeMillis();
      LOG.info("HDFS took:" + (timeEnd - timeStart) + " to get ready");
    }
  }
  
  static void launchHBaseReadyStateThread(final LaunchConfig launchConfig, final CountDownLatch latch) {
    launchConfig.threadCount++;
    Thread thread = new Thread(new Runnable() {

      @Override
      public void run() {
        Logger.getLogger("rg.apache.hadoop.hbase").setLevel(Level.ALL);
        try { 
          waitForZookeeperToBeReady();
          while (true) {
            try { 
              Configuration conf = new Configuration();
              conf.addResource(new Path("/etc/hadoop/conf/core-site.xml"));
              conf.addResource(new Path("/etc/hadoop/conf/hdfs-site.xml"));
              conf.addResource(new Path("/etc/hbase/conf/hbase-site.xml"));
              Connection connection = ConnectionFactory.createConnection(conf);              
              if (connection != null) {
                try { 
                  LOG.info("Testing HBase connection...");
                  Table table = connection.getTable(TableName.META_TABLE_NAME);
                  try { 
                    ResultScanner scanner = table.getScanner(new Scan());
                    try { 
                      scanner.next();
                    }
                    finally { 
                      scanner.close();
                    }
                  }
                  finally { 
                    table.close();
                  }
                  LOG.info("HBase Online");
                }
                finally { 
                  LOG.info("HBase test connection complete.");
                  connection.close();
                }
                break;
              }
            }
            catch (IOException e) { 
              LOG.info("waitForHBaseThread threw Exception:" + StringUtils.stringifyException(e));
            }
            try {
              // if someone triggered a shutdown, then exit immediately
              if (launchConfig.shutdownLatch.await(1, TimeUnit.MILLISECONDS) == true){ 
                break;
              }
            } catch (InterruptedException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
            
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        finally { 
          latch.countDown();
          // increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }
      } 
    });
    thread.start();
  }

  static void startLoginThread(final LaunchConfig launchConfig, final CountDownLatch loginLatch) {
    
    launchConfig.threadCount++;
    
    Thread thread = new Thread(new Runnable(){

      @Override
      public void run() {
        try { 
          UserGroupInformation.loginUserFromKeytab("hdfs/localhost@EXAMPLE.COM", "/etc/hadoop/conf/hdfs.keytab");
        }
        catch (Exception e) { 
          LOG.info("Login Failed With:" + StringUtils.stringifyException(e));
        }
        finally { 
          // trigger login latch 
          loginLatch.countDown();
          // and increment completed thread semaphore
          launchConfig.completedThreadSemaphore.release();
        }
      }});
    
    thread.start();
    
  }
  


  
  public static void main(String[] args) {
    Options opts = new Options();
    opts.addOption(COMMAND_NN_MEMORY, true,"namenode memory in megabytes");
    opts.addOption(COMMAND_DN_MEMORY, true,"datanode memory in megabytes");
    //opts.addOption(COMMAND_FORMAT_HDFS, false,"format namenode");

    removeCryptographyRestrictions();
    
    Options printOpts = new Options();
    CommandLineParser parser = new GnuParser();

    try {
      CommandLine commandLine = parser.parse(opts, args, true);
      LaunchConfig launchConfig = new LaunchConfig();
      
      if (commandLine.hasOption(COMMAND_NN_MEMORY)) { 
        launchConfig.NNMemoryMB = Integer.parseInt(commandLine.getOptionValue(COMMAND_NN_MEMORY));
      }
      if (commandLine.hasOption(COMMAND_DN_MEMORY)) { 
        launchConfig.DNMemoryMB = Integer.parseInt(commandLine.getOptionValue(COMMAND_DN_MEMORY));
      }
      LOG.info("Launching Services");
      LaunchResult launchResult = launchServices(launchConfig);
      try {
        LOG.info("Waiting on Ready Latch");
        launchConfig.readyLatch.await();
        LOG.info("Ready Latch Triggered. Service Launch Status:" + launchResult.launchStatus.get());
      } catch (InterruptedException e1) {
      }
      
      if (!launchResult.launchStatus.get()) { 
        LOG.error("Failed to successfully launch services. Triggering shutdown latch");
        launchConfig.shutdownLatch.countDown();
      }
      LOG.info("Waiting for Launch Thread to Exit");
      try {
        launchResult.launchThread.join();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      LOG.info("Waiting for Launch Thread to Exited");
      
      System.exit(0);

    } catch (ParseException e) {
      System.err.println("options parsing failed: " + e.getMessage());
      printHelpMessage(printOpts);
      System.exit(-1);
    }
  }
}
