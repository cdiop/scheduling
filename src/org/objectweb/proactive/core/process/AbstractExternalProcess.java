/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive-support@inria.fr
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.core.process;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.process.filetransfer.CopyProtocol;
import org.objectweb.proactive.core.process.filetransfer.FileTransferWorkShop;
import org.objectweb.proactive.core.util.MessageLogger;
import org.objectweb.proactive.core.util.log.Loggers;


public abstract class AbstractExternalProcess extends AbstractUniversalProcess
    implements ExternalProcess {
    protected static Logger clogger = Logger.getLogger(AbstractExternalProcess.class.getName());
    protected static Logger fileTransferLogger = Logger.getLogger(Loggers.FILETRANSFER);
    protected static final boolean IS_WINDOWS_SYSTEM = System.getProperty(
            "os.name").toLowerCase().startsWith("win");
    protected Process externalProcess;
    private boolean shouldRun;
    protected boolean closeStream = false;
    protected MessageLogger inputMessageLogger;
    protected MessageLogger errorMessageLogger;
    protected MessageSink outputMessageSink;
    private ThreadActivityMonitor inThreadMonitor;
    private ThreadActivityMonitor errThreadMonitor;
    private FileTransferWorkShop ftsDeploy = null;
    private FileTransferWorkShop ftsRetrieve = null;
    protected String FILE_TRANSFER_DEFAULT_PROTOCOL=null;

    //
    // -- CONSTRUCTORS -----------------------------------------------
    //
    protected AbstractExternalProcess() {
    }

    public AbstractExternalProcess(MessageLogger messageLogger) {
        this(messageLogger, messageLogger, null);
    }

    public AbstractExternalProcess(MessageLogger inputMessageLogger,
        MessageLogger errorMessageLogger) {
        this(inputMessageLogger, errorMessageLogger, null);
    }

    public AbstractExternalProcess(MessageLogger inputMessageLogger,
        MessageLogger errorMessageLogger, MessageSink outputMessageSink) {
        this.inputMessageLogger = inputMessageLogger;
        this.errorMessageLogger = errorMessageLogger;
        this.outputMessageSink = outputMessageSink;
    }

    //
    // -- PUBLIC METHODS -----------------------------------------------
    //
    //
    // -- implements ExternalProcess -----------------------------------------------
    //
    public void closeStream() {
        this.closeStream = true;
    }

    public MessageLogger getInputMessageLogger() {
        return inputMessageLogger;
    }

    public MessageLogger getErrorMessageLogger() {
        return errorMessageLogger;
    }

    public MessageSink getOutputMessageSink() {
        return outputMessageSink;
    }

    public void setInputMessageLogger(MessageLogger inputMessageLogger) {
        checkStarted();
        this.inputMessageLogger = inputMessageLogger;
    }

    public void setErrorMessageLogger(MessageLogger errorMessageLogger) {
        checkStarted();
        this.errorMessageLogger = errorMessageLogger;
    }

    public void setOutputMessageSink(MessageSink outputMessageSink) {
        checkStarted();
        this.outputMessageSink = outputMessageSink;
    }
    
    public FileTransferWorkShop getFileTransferWorkShopDeploy(){
    	
    	if(ftsDeploy==null)
    		ftsDeploy = new FileTransferWorkShop(getFileTransferDefaultCopyProtocol());
    	
		return ftsDeploy;
    }
    
    public FileTransferWorkShop getFileTransferWorkShopRetrieve(){
    	
    	if(ftsRetrieve==null)
    		ftsRetrieve = new FileTransferWorkShop(getFileTransferDefaultCopyProtocol());
    	
		return ftsRetrieve;
    }
    
    public String getFileTransferDefaultCopyProtocol(){
    	return FILE_TRANSFER_DEFAULT_PROTOCOL;
    }
    
    //
    // -- PROTECTED METHODS -----------------------------------------------
    //
    protected abstract String buildCommand();

    protected String buildEnvironmentCommand() {
        if (environment == null) {
            return "";
        }
        if (IS_WINDOWS_SYSTEM) {
            return buildWindowsEnvironmentCommand();
        } else {
            return buildUnixEnvironmentCommand();
        }
    }

    protected String buildWindowsEnvironmentCommand() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < environment.length; i++) {
            inputMessageLogger.log("      exporting variable " +
                environment[i]);
            sb.append("set ");
            sb.append(environment[i]);
            sb.append(" ; ");
        }
        return sb.toString();
    }

    protected String buildUnixEnvironmentCommand() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < environment.length; i++) {
            inputMessageLogger.log("      exporting variable " +
                environment[i]);
            sb.append("export ");
            sb.append(environment[i]);
            sb.append(" ; ");
        }
        return sb.toString();
    }

    protected void internalStartProcess(String commandToExecute)
        throws java.io.IOException {
        try {
            externalProcess = Runtime.getRuntime().exec(commandToExecute);
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
                        externalProcess.getInputStream()));
            java.io.BufferedReader err = new java.io.BufferedReader(new java.io.InputStreamReader(
                        externalProcess.getErrorStream()));
            java.io.BufferedWriter out = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                        externalProcess.getOutputStream()));
            handleProcess(in, out, err);
        } catch (java.io.IOException e) {
            isFinished = true;
            throw e;
        }
    }

    protected void internalStopProcess() {
        if (externalProcess != null) {
            externalProcess.destroy();
        }
        if (outputMessageSink != null) {
            outputMessageSink.setMessage(null);
        }
    }

    protected int internalWaitFor() throws InterruptedException {
        return externalProcess.waitFor();
    }

    /**
     * Try all the protocols until one is successful.
     */
    protected void internalStartFileTransfer(FileTransferWorkShop fts){
    	
    	CopyProtocol[] copyProtocol=fts.getCopyProtocols();
    	boolean success=false;
    	
    	if(fileTransferLogger.isDebugEnabled())
    		fileTransferLogger.debug("Using the following FileTransferWorkShop:\n"+fts);
    	
    	if(!fts.check()) return; //No files to transfer or some error.
    	
    	/* Try all the protocols for this FileTransferStructure
    	 * until one of them is successful */
    	for(int i=0; i<copyProtocol.length && !success; i++){
    		fileTransferLogger.info("Trying copyprotocol: "+copyProtocol[i].getProtocolName());
    		if(!copyProtocol[i].checkProtocol()){
    			logger.error("Protocol check failed");
    			continue;
    		}
    		//if can't handle the default protocol 
    		//then try the internal file transfer
    		if(copyProtocol[i].isDefaultProtocol()
    				&& copyProtocol[i].isDummyProtocol()) {
    			if(fileTransferLogger.isDebugEnabled())
    				fileTransferLogger.debug("Trying protocol internal filetransfer");
    			success=internalFileTransferDefaultProtocol();
    		}
    		//else simply try to start the filetransfer
    		else 
    			success=copyProtocol[i].startFileTransfer();
    	}

    	if(success)
    		fileTransferLogger.info("FileTransfer was successful");
    	else
    		fileTransferLogger.info("FileTransfer faild");
    }

    /**
     * This method should be redefined on every protocol that 
     * internaly implements the file transfer. Ex: Unicore
     * @return true if and only if successful.
     */
    protected boolean internalFileTransferDefaultProtocol(){
    	
    	//The default is false, to keep on trying the protocols
    	return false;
    }
    
    protected void handleProcess(java.io.BufferedReader in,
        java.io.BufferedWriter out, java.io.BufferedReader err) {
        if (closeStream) {
            try {
                //the sleep might be needed for processes that fail if
                // we close the in/err too early
                Thread.sleep(200);
                out.close();
                err.close();
                in.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            handleInput(in);
            handleOutput(out);
            handleError(err);
        }
    }

    protected void handleInput(java.io.BufferedReader in) {
        if (inputMessageLogger == null) {
            return;
        }
        inThreadMonitor = new ThreadActivityMonitor();
        Runnable r = new ProcessInputHandler(in, inputMessageLogger,
                inThreadMonitor);
        Thread t = new Thread(r, "IN -> " + getShortName(getCommand(), 20));
        t.start();
    }

    protected void handleError(java.io.BufferedReader err) {
        if (errorMessageLogger == null) {
            return;
        }
        errThreadMonitor = new ThreadActivityMonitor();
        Runnable r = new ProcessInputHandler(err, errorMessageLogger,
                errThreadMonitor);
        Thread t = new Thread(r, "ERR -> " + getShortName(getCommand(), 20));
        t.start();
    }

    protected void handleOutput(java.io.BufferedWriter out) {
        if (outputMessageSink == null) {
            return;
        }

        //System.out.println("my output sink :"+outputMessageSink.toString());
        Runnable r = new ProcessOutputHandler(out, outputMessageSink);
        Thread t = new Thread(r, "OUT -> " + getShortName(getCommand(), 20));
        t.start();
    }

    //
    // -- PRIVATE METHODS -----------------------------------------------
    //
    private final String getShortName(String name, int length) {
        return name.substring(0, Math.min(name.length(), length));
    }

    private final void waitForMonitoredThread() {
        do {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
            }
        } while (errThreadMonitor.isActive() || inThreadMonitor.isActive());
    }

    private void writeObject(java.io.ObjectOutputStream out)
        throws java.io.IOException {
        if (isStarted) {
            //if the process is started, we have to remove the external process
            // which is now not Serializable:UnixProcess or WindowsProcess
            externalProcess = null;
        }
        out.defaultWriteObject();
    }

    //
    // -- INNER CLASSES -----------------------------------------------
    //
    private static class ThreadActivityMonitor implements java.io.Serializable {
        private boolean isActive;

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean b) {
            isActive = b;
        }
    }

    /**
     * Implementation of a MessageLogger that output all messages to the standard output
     */
    public static class StandardOutputMessageLogger implements MessageLogger,
        java.io.Serializable {
        public StandardOutputMessageLogger() {
            //messageLogger.addAppender(new ConsoleAppender(new PatternLayout("%-5p %m %n")));
        }

        public void log(String message) {
            messageLogger.info(message);
        }

        public void log(Throwable t) {
            t.printStackTrace();
        }

        public void log(String message, Throwable t) {
            messageLogger.info(message);
            t.printStackTrace();
        }
    }

    // end inner class StandardOutputMessageLogger

    /**
     * Implementation of a MessageLogger that discard all output
     */
    public static class NullMessageLogger implements MessageLogger,
        java.io.Serializable {
        public NullMessageLogger() {
        }

        public void log(String message) {
        }

        public void log(Throwable t) {
        }

        public void log(String message, Throwable t) {
        }
    }

    // end inner class NullMessageLogger

    /**
     * Implementation of a MessageSink that can receive one message at a time
     */
    public static class SimpleMessageSink implements MessageSink,
        java.io.Serializable {
        private String message;
        private boolean isActive = true;

        public synchronized String getMessage() {
            if (!isActive) {
                return null;
            }
            while ((message == null) && isActive) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            String messageToSend = message;
            message = null;
            notifyAll();
            return messageToSend;
        }

        public synchronized void setMessage(String messageToPost) {
            if (!isActive) {
                return;
            }
            while ((message != null) && isActive) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            if (messageToPost == null) {
                isActive = false;
            }
            this.message = messageToPost;
            notifyAll();
        }

        public synchronized boolean hasMessage() {
            return message != null;
        }

        public synchronized boolean isActive() {
            return isActive;
        }
    }

    // end inner class SimpleMessageSink

    /**
     * This class reads all messages from an input and log them using a
     * MessageLogger
     */
    protected class ProcessInputHandler implements Runnable {
        private java.io.BufferedReader in;
        private MessageLogger logger;
        private ThreadActivityMonitor threadMonitor;

        public ProcessInputHandler(java.io.BufferedReader in,
            MessageLogger logger, ThreadActivityMonitor threadMonitor) {
            this.in = in;
            this.logger = logger;
            this.threadMonitor = threadMonitor;
        }

        public void run() {
            if (AbstractExternalProcess.clogger.isDebugEnabled()) {
                AbstractExternalProcess.clogger.debug("Process started Thread=" +
                    Thread.currentThread().getName());
            }

            //
            try {
                while (true) {
                    //threadMonitor.setActive(false);
                    //                    if (AbstractExternalProcess.clogger.isDebugEnabled()) {
                    //						AbstractExternalProcess.clogger.debug(
                    //                            "ProcessInputHandler before readLine()");
                    //                    }
                    String s = in.readLine();
                    if (AbstractExternalProcess.clogger.isDebugEnabled()) {
                        //                        AbstractExternalProcess.clogger.debug(
                        //                            "ProcessInputHandler after readLine() s=" + s);
                        AbstractExternalProcess.clogger.debug(s);
                    }

                    //
                    threadMonitor.setActive(true);
                    if (s == null) {
                        break;
                    } else {
                        logger.log(s);
                    }
                }
            } catch (java.io.IOException e) {
                logger.log(e);
            } finally {
                isFinished = true;
                threadMonitor.setActive(false);
                try {
                    in.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
                logger.log("Process finished Thread=" +
                    Thread.currentThread().getName());
            }
        }
    }

    // end inner class ProcessInputHandler

    /**
     * This class uses a MessageSink to write all messages produced
     * in a given output
     */
    protected class ProcessOutputHandler implements Runnable {
        private java.io.BufferedWriter out;
        private MessageSink messageSink;

        public ProcessOutputHandler(java.io.BufferedWriter out,
            MessageSink messageSink) {
            this.out = out;
            this.messageSink = messageSink;
        }

        public void run() {
            try {
                while (true) {
                    waitForMonitoredThread();
                    //System.out.println("ProcessOutputHandler before getMessage()");
                    String message = messageSink.getMessage();
                    if (message == null) {
                        break;
                    }
                    try {
                        out.write(message);
                        out.newLine();
                        out.flush();
                        //System.out.println("ProcessOutputHandler writing "+message);
                    } catch (java.io.IOException e) {
                        break;
                    }
                    message = null;
                }
            } finally {
                isFinished = true;
                waitForMonitoredThread();
                try {
                    out.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // end inner class ProcessOutputHandler
}
