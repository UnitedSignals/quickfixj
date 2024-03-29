/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved. 
 * 
 * This file is part of the QuickFIX FIX Engine 
 * 
 * This file may be distributed under the terms of the quickfixengine.org 
 * license as defined by quickfixengine.org and appearing in the file 
 * LICENSE included in the packaging of this file. 
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING 
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE. 
 * 
 * See http://www.quickfixengine.org/LICENSE for licensing information. 
 * 
 * Contact ask@quickfixengine.org if any conditions of this licensing 
 * are not clear to you.
 ******************************************************************************/

package quickfix;

import org.quickfixj.CharsetSupport;
import quickfix.field.converter.UtcTimestampConverter;

import java.io.*;

/**
 * File log implementation. THIS CLASS IS PUBLIC ONLY TO MAINTAIN COMPATIBILITY
 * WITH THE QUICKFIX JNI. IT SHOULD ONLY BE CREATED USING A FACTORY.
 * 
 * @see quickfix.FileLogFactory
 */
public class FileLog extends AbstractLog {
    private static final byte[] TIME_STAMP_DELIMETER;
    static {
        try {
            TIME_STAMP_DELIMETER = ": ".getBytes(CharsetSupport.getCharset());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    private SessionID sessionID;
    private String messagesFileName;
    private String eventFileName;
    private boolean syncAfterWrite;

    private FileOutputStream messages;
    private FileOutputStream events;
    
    private boolean includeMillis;
    private boolean includeTimestampForMessages;
    private final boolean logVerbose;
    
    FileLog(String path, SessionID sessionID, boolean includeMillis, boolean includeTimestampForMessages, boolean logHeartbeats) throws FileNotFoundException {
        this.sessionID = sessionID;
        String sessionName = FileUtil.sessionIdFileName(sessionID);
        
        setLogHeartbeats(logHeartbeats);
        
        String prefix = FileUtil.fileAppendPath(path, sessionName + ".");
        messagesFileName = prefix + "messages.log";
        eventFileName = prefix + "event.log";

        File directory = new File(messagesFileName).getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        this.includeMillis = includeMillis;
        this.includeTimestampForMessages = includeTimestampForMessages;
        this.logVerbose = Boolean.getBoolean("quickfixj.logging.verbose");
        
        openLogStreams(true);
    }

    private void openLogStreams(boolean append) throws FileNotFoundException {
        messages = new FileOutputStream(messagesFileName, append);
        events = new FileOutputStream(eventFileName, append);
    }

    protected void logIncoming(String message) {
        writeMessage(messages, message, false);
    }

    protected void logOutgoing(String message) {
        writeMessage(messages, message, false);
    }

    private void writeMessage(FileOutputStream stream, String message, boolean forceTimestamp) {
        if ((!message.contains("35=W") /* MarketDataSnapshotFullRefresh */ && !message.contains("35=S") /* Quote */) || logVerbose) {
            try {
                if (forceTimestamp || includeTimestampForMessages) {
                    writeTimeStamp(stream);
                }
                stream.write(message.getBytes(CharsetSupport.getCharset()));
                stream.write('\n');
                stream.flush();
                if (syncAfterWrite) {
                    stream.getFD().sync();
                }
            } catch (IOException e) {
                LogUtil.logThrowable(sessionID, "error writing message to log", e);
            }
        }
    }

    public void onEvent(String message) {
        writeMessage(events, message, true);
    }

    public void onErrorEvent(String message) {
        writeMessage(events, message, true);
    }
    
    private void writeTimeStamp(OutputStream out) throws IOException {
        String formattedTime = UtcTimestampConverter.convert(SystemTime.getDate(), includeMillis);
        out.write(formattedTime.getBytes(CharsetSupport.getCharset()));
        out.write(TIME_STAMP_DELIMETER);
    }

    String getEventFileName() {
        return eventFileName;
    }

    String getMessagesFileName() {
        return messagesFileName;
    }

    public void setSyncAfterWrite(boolean syncAfterWrite) {
        this.syncAfterWrite = syncAfterWrite;
    }
    
    public void closeFiles() throws IOException {
        messages.close();
        events.close();
    }
    
    /**
     * Deletes the log files. Do not perform any log operations while performing
     * this operation.
     */
    public void clear() {
        try {
            closeFiles();
            openLogStreams(false);
        } catch (IOException e) {
            System.err.println("Could not clear log: "+getClass().getName());
        }
    }
}