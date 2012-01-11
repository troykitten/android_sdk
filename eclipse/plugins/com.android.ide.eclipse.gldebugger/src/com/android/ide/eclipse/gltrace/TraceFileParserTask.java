/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.gltrace;

import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.Function;
import com.android.ide.eclipse.gltrace.format.GLAPISpec;
import com.android.ide.eclipse.gltrace.format.GLMessageFormatter;
import com.android.ide.eclipse.gltrace.model.GLCall;
import com.android.ide.eclipse.gltrace.model.GLFrame;
import com.android.ide.eclipse.gltrace.model.GLTrace;
import com.android.ide.eclipse.gltrace.state.GLStateTransform;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TraceFileParserTask implements IRunnableWithProgress {
    private static final TraceFileReader sReader = new TraceFileReader();

    private static final GLMessageFormatter sGLMessageFormatter =
            new GLMessageFormatter(GLAPISpec.getSpecs());

    private final Display mDisplay;
    private final int mThumbHeight;
    private final int mThumbWidth;

    private String mTraceFilePath;
    private RandomAccessFile mFile;

    private List<GLCall> mGLCalls;
    private List<List<GLStateTransform>> mStateTransformsPerCall;
    private Set<Integer> mGLContextIds;

    private GLTrace mTrace;

    /**
     * Construct a GL Trace file parser.
     * @param path path to trace file
     * @param thumbDisplay display to use to create thumbnail images
     * @param thumbWidth width of thumbnail images
     * @param thumbHeight height of thumbnail images
     */
    public TraceFileParserTask(String path, Display thumbDisplay, int thumbWidth,
            int thumbHeight) {
        try {
            mFile = new RandomAccessFile(path, "r"); //$NON-NLS-1$
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

        mDisplay = thumbDisplay;
        mThumbWidth = thumbWidth;
        mThumbHeight = thumbHeight;

        mTraceFilePath = path;
        mGLCalls = new ArrayList<GLCall>();
        mStateTransformsPerCall = new ArrayList<List<GLStateTransform>>();
        mGLContextIds = new TreeSet<Integer>();
    }

    private void addMessage(int index, long traceFileOffset, GLMessage msg, long startTime) {
        Image previewImage = null;
        if (mDisplay != null) {
            previewImage = ProtoBufUtils.getScaledImage(mDisplay, msg, mThumbWidth, mThumbHeight);
        }

        String formattedMsg;
        try {
            formattedMsg = sGLMessageFormatter.formatGLMessage(msg);
        } catch (Exception e) {
            formattedMsg = String.format("%s()", msg.getFunction().toString()); //$NON-NLS-1$
        }

        GLCall c = new GLCall(index,
                                startTime,
                                traceFileOffset,
                                formattedMsg,
                                previewImage,
                                msg.getFunction(),
                                msg.hasFb(),
                                msg.getContextId(),
                                msg.getDuration());

        mGLCalls.add(c);
        mStateTransformsPerCall.add(GLStateTransform.getTransformsFor(msg));
        mGLContextIds.add(Integer.valueOf(c.getContextId()));
    }

    /**
     * Parse the entire file and create a {@link GLTrace} object that can be retrieved
     * using {@link #getTrace()}.
     */
    @Override
    public void run(IProgressMonitor monitor) throws InvocationTargetException,
            InterruptedException {
        monitor.beginTask("Parsing OpenGL Trace File", IProgressMonitor.UNKNOWN);

        List<GLFrame> glFrames = null;

        try {
            GLMessage msg = null;
            int msgCount = 0;
            long filePointer = mFile.getFilePointer();

            // counters that maintain some statistics about the trace messages
            long minTraceStartTime = Long.MAX_VALUE;
            int maxContextId = -1;

            while ((msg = sReader.getMessageAtOffset(mFile, 0)) != null) {
                if (minTraceStartTime > msg.getStartTime()) {
                    minTraceStartTime = msg.getStartTime();
                }

                if (maxContextId < msg.getContextId()) {
                    maxContextId = msg.getContextId();
                }

                addMessage(msgCount, filePointer, msg, msg.getStartTime() - minTraceStartTime);

                filePointer = mFile.getFilePointer();
                msgCount++;

                if (monitor.isCanceled()) {
                    throw new InterruptedException();
                }
            }

            if (maxContextId > 0) {
                // if there are multiple contexts, then the calls may arrive at the
                // host out of order. So we perform a sort based on the invocation time.
                Collections.sort(mGLCalls, new Comparator<GLCall>() {
                    @Override
                    public int compare(GLCall c1, GLCall c2) {
                        long diff = (c1.getStartTime() - c2.getStartTime());

                        // We could return diff casted to an int. But in Java, casting
                        // from a long to an int truncates the bits and will not preserve
                        // the sign. So we resort to comparing the diff to 0 and returning
                        // the sign.
                        if (diff == 0) {
                            return 0;
                        } else if (diff > 0) {
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                });
            }

            glFrames = createFrames(mGLCalls);
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        } finally {
            try {
                mFile.close();
            } catch (IOException e) {
                // ignore exception while closing file
            }
            monitor.done();
        }

        File f = new File(mTraceFilePath);
        TraceFileInfo fileInfo = new TraceFileInfo(mTraceFilePath, f.length(), f.lastModified());
        mTrace = new GLTrace(fileInfo, glFrames, mGLCalls, mStateTransformsPerCall,
                                new ArrayList<Integer>(mGLContextIds));
    }

    /** Assign GL calls to GL Frames. */
    private List<GLFrame> createFrames(List<GLCall> calls) {
        List<GLFrame> glFrames = new ArrayList<GLFrame>();
        int startCallIndex = 0;
        int frameIndex = 0;

        for (int i = 0; i < calls.size(); i++) {
            GLCall c = calls.get(i);
            if (c.getFunction() == Function.eglSwapBuffers) {
                glFrames.add(new GLFrame(frameIndex, startCallIndex, i));
                startCallIndex = i + 1;
                frameIndex++;
            }
        }

        // assign left over calls at the end to the last frame
        if (startCallIndex != mGLCalls.size()) {
            glFrames.add(new GLFrame(frameIndex, startCallIndex, mGLCalls.size() - 1));
        }

        return glFrames;
    }

    /**
     * Retrieve the trace object constructed from messages in the trace file.
     */
    public GLTrace getTrace() {
        return mTrace;
    }
}