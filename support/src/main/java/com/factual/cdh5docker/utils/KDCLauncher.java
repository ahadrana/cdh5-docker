package com.factual.cdh5docker.utils;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KerberosKeyFactory;
import org.apache.directory.server.kerberos.shared.keytab.Keytab;
import org.apache.directory.server.kerberos.shared.keytab.KeytabEntry;
import org.apache.directory.shared.kerberos.KerberosTime;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.minikdc.MiniKdc;
import org.apache.hadoop.security.ssl.KeyStoreTestUtil;
import org.apache.hadoop.util.Shell;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;


public class KDCLauncher {

  static Logger LOG  = Logger.getLogger(KDCLauncher.class); 
  
  private static HdfsConfiguration baseConf;
  private static File baseDir;
  private static MiniKdc kdc;
  
  static { 
    Logger.getLogger("org.apache.directory").setLevel(Level.ALL);
  }
  
  public static class KeytabDeclaration {
    public String username;
    public List<String> principals;
    public KeytabDeclaration(String username,String ... principals) {
      this.username = username;
      this.principals = Lists.newArrayList(principals);
    }
  }
  
  public static void createKeyTabs(MiniKdc kdc,File baseDir,KeytabDeclaration ... declarations) throws Exception { 
    Map<String,String> passwordMap = Maps.newTreeMap();
    // generate principals for the set of ALL principals we will be creating ... 
    for (KeytabDeclaration declaration : declarations) { 
      for (String principalName : declaration.principals) {
        if (!passwordMap.containsKey(principalName)) { 
          String generatedPassword = UUID.randomUUID().toString();
          passwordMap.put(principalName, generatedPassword);
          System.out.println("Create Principal:" + principalName);
          kdc.createPrincipal(principalName,generatedPassword);
        }
      }
      
    }
    // next generate keytabs from declarations 
    for (KeytabDeclaration declaration : declarations) {
      Keytab keytab = new Keytab();
      List<KeytabEntry> entries = new ArrayList<KeytabEntry>();
      for (String principal : declaration.principals) { 
        String keytabPrincipal = principal + "@" + kdc.getRealm();
        System.out.println("FQP is:" + keytabPrincipal);
        KerberosTime timestamp = new KerberosTime();
        String generatedPassword = passwordMap.get(principal);
        
        for (Map.Entry<EncryptionType, EncryptionKey> entry : KerberosKeyFactory.getKerberosKeys(keytabPrincipal, generatedPassword).entrySet()) {
          EncryptionKey ekey = entry.getValue();
          byte keyVersion = (byte) ekey.getKeyVersion();
          entries.add(new KeytabEntry(keytabPrincipal, 1L, timestamp, keyVersion,ekey));
        }
      }
      keytab.setEntries(entries);
      File keytabPath = new File(baseDir, declaration.username + ".keytab");
      keytab.write(keytabPath);
      
      // chown to appropriate user .. 
      Shell.execCommand("chown",declaration.username,keytabPath.toString());
    }
  }

  
  public static void startKDC(String orgName) throws Exception {
    
    baseConf = new HdfsConfiguration();
    baseDir = new File("/etc/krb5kdc");
    FileUtil.fullyDelete(baseDir);
    assertTrue(baseDir.mkdirs());

    Properties kdcConf = MiniKdc.createConf();
    
    kdcConf.setProperty(MiniKdc.KDC_PORT,"88");
    if (orgName != null) { 
      kdcConf.setProperty(MiniKdc.ORG_NAME, orgName);
    }
    
    kdc = new MiniKdc(kdcConf, baseDir);
    kdc.start();
    
    /** copy krb5 conf **/
    File krb5Path = new File("/etc/krb5.conf");
    krb5Path.delete();
    Files.copy(kdc.getKrb5conf().toPath(),krb5Path.toPath());

    /** setup a test user **/
    Shell.execCommand("useradd","-m","-U","test-user");
    
    /** create users **/
    createKeyTabs(
        kdc,
        // files go in /etc/hadoop/conf (per convention)
        new File("/etc/hadoop/conf"),
        
        new KeytabDeclaration("test-user","test-user/localhost"),
        new KeytabDeclaration("hdfs","hdfs/localhost","HTTP/localhost"),
        new KeytabDeclaration("mapred","mapred/localhost","HTTP/localhost"),
        new KeytabDeclaration("yarn","yarn/localhost","HTTP/localhost"),
        new KeytabDeclaration("hbase","hbase/localhost","HTTP/localhost"),
        new KeytabDeclaration("zookeeper","zookeeper/localhost","HTTP/localhost"));

    /*
    SecurityUtil.setAuthenticationMethod(AuthenticationMethod.KERBEROS,baseConf);
    UserGroupInformation.setConfiguration(baseConf);
    assertTrue("Expected configuration to enable security",
      UserGroupInformation.isSecurityEnabled());

    String hdfsPrincipal = userName + "/" + krbInstance + "@" + kdc.getRealm();
    String spnegoPrincipal = "HTTP/" + krbInstance + "@" + kdc.getRealm();
     
    baseConf.set(DFS_NAMENODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    baseConf.set(DFS_NAMENODE_KEYTAB_FILE_KEY, keytab);
    baseConf.set(DFS_DATANODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    baseConf.set(DFS_DATANODE_KEYTAB_FILE_KEY, keytab);
    baseConf.set(DFS_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL_KEY, spnegoPrincipal);
    baseConf.set(DFS_JOURNALNODE_KEYTAB_FILE_KEY, keytab);
    baseConf.set(DFS_JOURNALNODE_KERBEROS_PRINCIPAL_KEY, hdfsPrincipal);
    baseConf.set(DFS_JOURNALNODE_KERBEROS_INTERNAL_SPNEGO_PRINCIPAL_KEY,
      spnegoPrincipal);
    baseConf.setBoolean(DFS_BLOCK_ACCESS_TOKEN_ENABLE_KEY, true);
    baseConf.set(DFS_DATA_TRANSFER_PROTECTION_KEY, "authentication");
    baseConf.set(DFS_HTTP_POLICY_KEY, HttpConfig.Policy.HTTPS_ONLY.name());
    baseConf.set(DFS_NAMENODE_HTTPS_ADDRESS_KEY, "localhost:0");
    baseConf.set(DFS_DATANODE_HTTPS_ADDRESS_KEY, "localhost:0");
    baseConf.set(DFS_JOURNALNODE_HTTPS_ADDRESS_KEY, "localhost:0");
    baseConf.setInt(IPC_CLIENT_CONNECT_MAX_RETRIES_ON_SASL_KEY, 10);
    
    */

    String keystoresDir = baseDir.getAbsolutePath();
    KeyStoreTestUtil.setupSSLConfig(keystoresDir, keystoresDir, baseConf, false);

    
  }

  public static void stopKDC() { 
    if (kdc != null) {
      kdc.stop();
    }
  }
  

  private static final String ORG_NAME_OPTION = "org";

  public static void main(String[] args)throws Exception {
    
    Options opts = new Options();
    Option commandOption = new Option(ORG_NAME_OPTION, true, "ORG Name Required");
    commandOption.setRequired(false);
    opts.addOption(commandOption);
    
    Options printOpts = new Options();
    
    CommandLineParser parser = new GnuParser();

    try {
      CommandLine commandLine = parser.parse(opts, args, true);
      String orgnaization = commandLine.getOptionValue(ORG_NAME_OPTION);

      // init config 
      startKDC(orgnaization);
      try {
        Thread.sleep(Long.MAX_VALUE);
      }
      catch (InterruptedException e) { 
      }
      finally { 
        stopKDC();
      }


    } catch (ParseException e) {
      System.err.println("options parsing failed: " + e.getMessage());
      printHelpMessage(printOpts);
      System.exit(-1);
    }
    
  }
  
  private static void printHelpMessage(Options options) {
    System.out.println("KDC Launcher.");
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    formatter.printHelp("general options are:", options);
  }
  
}
