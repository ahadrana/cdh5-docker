package com.factual.cdh5docker.utils;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FsShell;
import org.apache.hadoop.util.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;

public class DirectoryUtils {

  
  
  static Logger LOG = Logger.getLogger(DirectoryUtils.class);
  
  private static final String COMMAND_OPTION = "cmd";

  private static final String COMMAND_CREATE_SERVICE_DIRS = "createServiceDirs";
  
  private static String commands[] = {COMMAND_CREATE_SERVICE_DIRS};
  
  private static int runCmd(FsShell shell, String... args) throws IOException {
    StringBuilder cmdline = new StringBuilder("RUN:");
    for (String arg : args) cmdline.append(" " + arg);
    LOG.info(cmdline.toString());
    try {
      int exitCode;
      exitCode = shell.run(args);
      LOG.info("RUN: "+args[0]+" exit=" + exitCode);
      return exitCode;
    } catch (IOException e) {
      LOG.error("RUN: "+args[0]+" IOException="+e.getMessage());
      throw e;
    } catch (RuntimeException e) {
      LOG.error("RUN: "+args[0]+" RuntimeException="+e.getMessage());
      throw e;
    } catch (Exception e) {
      LOG.error("RUN: "+args[0]+" Exception="+e.getMessage());
      throw new IOException(StringUtils.stringifyException(e));
    }
  }
  
  private static void printHelpMessage(Options options) {
    System.out.println("Directory Utility.");
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("-cmd <"+Lists.newArrayList(commands) + "> [OPTIONS]", new Options());
    formatter.setSyntaxPrefix("");
    formatter.printHelp("general options are:", options);
  }
  
  public static void main(String[] args) throws IOException {
    
    Options opts = new Options();
    Option commandOption = new Option(COMMAND_OPTION, true, "Command (required)");
    commandOption.setRequired(true);
    opts.addOption(commandOption);
    
    Options printOpts = new Options();
    
    if (args.length < 1) {
      printHelpMessage(printOpts);
      System.exit(-1);
    }

    CommandLineParser parser = new GnuParser();

    try {
      CommandLine commandLine = parser.parse(opts, args, true);
      String command = commandLine.getOptionValue(COMMAND_OPTION);
      if (command.equalsIgnoreCase(COMMAND_CREATE_SERVICE_DIRS)) { 
        createServiceDirs();
        System.exit(0);
      }
      else { 
        System.exit(-1);
      }
      

    } catch (ParseException e) {
      System.err.println("options parsing failed: " + e.getMessage());
      printHelpMessage(printOpts);
      System.exit(-1);
    }
  }
    
  static void createServiceDirs() throws IOException { 
    Configuration conf = new Configuration();
    // get the default file system 
    FsShell shell = new FsShell(conf);

    runCmd(shell,"-mkdir","-p", "/tmp/hadoop-yarn/staging/history/done_intermediate");
    
    
    runCmd(shell,"-mkdir","-p","/tmp/hadoop-yarn/staging/history/done_intermediate");
    runCmd(shell,"-chown","-R","mapred:mapred","/tmp/hadoop-yarn/staging");
    runCmd(shell,"-chmod","-R","1777","/tmp");
    runCmd(shell,"-mkdir","-p","/var/log/hadoop-yarn");
    runCmd(shell,"-chown","yarn:mapred","/var/log/hadoop-yarn");
    runCmd(shell,"-mkdir","-p","/user/hdfs");
    runCmd(shell,"-chown","hdfs","/user/hdfs");
    runCmd(shell,"-mkdir","/hbase");
    runCmd(shell,"-chown","hbase","/hbase");
    
  }
  
}
