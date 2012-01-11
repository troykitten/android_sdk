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

package com.android.ide.eclipse.gltrace.model;

import com.android.ide.eclipse.gltrace.GLProtoBuf;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.Function;

import org.eclipse.swt.graphics.Image;

/**
 * A GLCall is the in memory representation of a single {@link GLProtoBuf.GLMessage}.
 *
 * Some protocol buffer messages have a large amount of image data packed in them. Rather
 * than storing all of that in memory, the GLCall stores a thumbnail image, and an offset
 * into the trace file corresponding to original protocol buffer message. If full image data
 * is required, the protocol buffer message can be recreated by reading the trace at the
 * specified offset.
 */
public class GLCall {
    /** Index of this call in the trace. */
    private final int mIndex;

    /** Time on device when this call was invoked. */
    private final long mStartTime;

    /** Offset of the protobuf message corresponding to this call in the trace file. */
    private final long mTraceFileOffset;

    /** Flag indicating whether the original protobuf message included FB data. */
    private final boolean mHasFb;

    /** Thumbnail image of the framebuffer if available. */
    private final Image mThumbnailImage;

    /** Full string representation of this call. */
    private final String mDisplayString;

    /** The actual GL Function called. */
    private final Function mFunction;

    /** GL Context identifier corresponding to the context of this call. */
    private final int mContextId;

    /** Duration of this call. */
    private final int mDuration;

    public GLCall(int index, long startTime, long traceFileOffset, String displayString,
            Image thumbnailImage, Function function, boolean hasFb, int contextId, int duration) {
        mIndex = index;
        mStartTime = startTime;
        mTraceFileOffset = traceFileOffset;
        mThumbnailImage = thumbnailImage;
        mDisplayString = displayString;
        mFunction = function;
        mHasFb = hasFb;
        mContextId = contextId;
        mDuration = duration;
    }

    public int getIndex() {
        return mIndex;
    }

    public long getOffsetInTraceFile() {
        return mTraceFileOffset;
    }

    public Function getFunction() {
        return mFunction;
    }

    public int getContextId() {
        return mContextId;
    }

    public boolean hasFb() {
        return mHasFb;
    }

    public Image getThumbnailImage() {
        return mThumbnailImage;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public int getDuration() {
        return mDuration;
    }

    @Override
    public String toString() {
        return mDisplayString;
    }
}