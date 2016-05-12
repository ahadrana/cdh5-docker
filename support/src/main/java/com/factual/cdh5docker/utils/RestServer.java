package com.factual.cdh5docker.utils;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;

import com.factual.cdh5docker.utils.ServiceLauncher.LaunchConfig;
import com.factual.cdh5docker.utils.ServiceLauncher.LaunchResult;


/** 
 * Simple Server to expose Core Services (HDFS/YARN/HBASE/ZK/KAFKA) restart via a REST endpoint. 
 * 
 * @author rana
 *
 */
public class RestServer {
  
  static LaunchConfig activeLaunchConfig;
  static LaunchResult activeLaunchStatus;
  
  
  private static final String PORT_OPTION = "p";

  private static void printHelpMessage(Options options) {
    System.out.println("Rest Server.");
    HelpFormatter formatter = new HelpFormatter();
    formatter.setSyntaxPrefix("");
    formatter.printHelp("general options are:", options);
  }

  public static void main(String[] args) throws Exception {
  
    Options opts = new Options();
    Option commandOption = new Option(PORT_OPTION, true, "Command (required)");
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
      int port = Integer.parseInt(commandLine.getOptionValue(PORT_OPTION));    
      
      Server server = new Server(port);
      
      Context context = new Context();
      
      context.setContextPath("/");
      context.addServlet(RestartServlet.class,"/restart");
      context.setAllowNullPathInfo(true);

      server.addHandler(context);
      
      
      server.start();
      
      server.join();
      
    } catch (ParseException e) {
      System.err.println("options parsing failed: " + e.getMessage());
      printHelpMessage(printOpts);
      System.exit(-1);
    }
  }
  
  public static class RestartServlet extends HttpServlet { 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)throws ServletException, IOException {
      if (activeLaunchConfig != null) { 
        try {
          activeLaunchConfig.shutdownLatch.countDown();
          activeLaunchStatus.readyLatch.await();
          activeLaunchStatus.launchThread.join();
          activeLaunchStatus = null;
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
        
      activeLaunchConfig = new LaunchConfig();
      activeLaunchStatus = ServiceLauncher.launchServices(activeLaunchConfig);
      try {
        activeLaunchStatus.readyLatch.await();
      } catch (InterruptedException e) {
      }
      if (activeLaunchStatus.launchStatus.get()) { 
        resp.getWriter().println("READY");
      }
      else { 
        resp.setStatus(500);
        resp.getWriter().println("FAILED");
      }
      resp.flushBuffer();
    }
  }
}
