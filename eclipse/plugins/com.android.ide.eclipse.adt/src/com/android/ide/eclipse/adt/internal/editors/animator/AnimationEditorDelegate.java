/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.animator;

import static com.android.ide.eclipse.adt.AdtConstants.EDITORS_NAMESPACE;

import com.android.ide.eclipse.adt.internal.editors.AndroidXmlCommonEditor;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Editor for /res/animator XML files.
 */
@SuppressWarnings("restriction")
public class AnimationEditorDelegate extends XmlEditorDelegate {

    public static class Creator implements IXmlEditorCreator {
        @Override
        @SuppressWarnings("unchecked")
        public AnimationEditorDelegate createForFile(
                AndroidXmlCommonEditor delegator,
                IFileEditorInput input,
                ResourceFolderType type) {
            if (ResourceFolderType.ANIM == type || ResourceFolderType.ANIMATOR == type) {
                return new AnimationEditorDelegate(delegator);
            }

            return null;
        }
    }

    /**
     * Old standalone-editor ID.
     * Use {@link AndroidXmlCommonEditor#ID} instead.
     */
    public static final String LEGACY_EDITOR_ID = EDITORS_NAMESPACE + ".animator.AnimationEditor"; //$NON-NLS-1$

    /** The tag used at the root */
    private String mRootTag;

    public AnimationEditorDelegate(AndroidXmlCommonEditor editor) {
        super(editor);
    }

    @Override
    public void createFormPages() {
        /* Disabled for now; doesn't work quite right
        try {
            addPage(new AnimatorTreePage(this));
        } catch (PartInitException e) {
            AdtPlugin.log(IStatus.ERROR, "Error creating nested page"); //$NON-NLS-1$
            AdtPlugin.getDefault().getLog().log(e.getStatus());
        }
        */
     }

    /**
     * Processes the new XML Model.
     *
     * @param xmlDoc The XML document, if available, or null if none exists.
     */
    @Override
    public void xmlModelChanged(Document xmlDoc) {
        Element rootElement = xmlDoc.getDocumentElement();
        if (rootElement != null) {
            mRootTag = rootElement.getTagName();
        }

        // create the ui root node on demand.
        initUiRootNode(false /*force*/);

        if (mRootTag != null
                && !mRootTag.equals(getUiRootNode().getDescriptor().getXmlLocalName())) {
            AndroidTargetData data = getEditor().getTargetData();
            if (data != null) {
                ElementDescriptor descriptor;
                if (getFolderType() == ResourceFolderType.ANIM) {
                    descriptor = data.getAnimDescriptors().getElementDescriptor(mRootTag);
                } else {
                    descriptor = data.getAnimatorDescriptors().getElementDescriptor(mRootTag);
                }
                // Replace top level node now that we know the actual type

                // Disconnect from old
                getUiRootNode().setEditor(null);
                getUiRootNode().setXmlDocument(null);

                // Create new
                setUiRootNode(descriptor.createUiNode());
                getUiRootNode().setXmlDocument(xmlDoc);
                getUiRootNode().setEditor(getEditor());
            }
        }

        if (getUiRootNode().getDescriptor() instanceof DocumentDescriptor) {
            getUiRootNode().loadFromXmlNode(xmlDoc);
        } else {
            getUiRootNode().loadFromXmlNode(rootElement);
        }
    }

    @Override
    public void initUiRootNode(boolean force) {
        // The manifest UI node is always created, even if there's no corresponding XML node.
        if (getUiRootNode() == null || force) {
            ElementDescriptor descriptor;
            boolean reload = false;
            AndroidTargetData data = getEditor().getTargetData();
            if (data == null) {
                descriptor = new DocumentDescriptor("temp", null /*children*/);
            } else {
                if (getFolderType() == ResourceFolderType.ANIM) {
                    descriptor = data.getAnimDescriptors().getElementDescriptor(mRootTag);
                } else {
                    descriptor = data.getAnimatorDescriptors().getElementDescriptor(mRootTag);
                }
                reload = true;
            }
            setUiRootNode(descriptor.createUiNode());
            getUiRootNode().setEditor(getEditor());

            if (reload) {
                onDescriptorsChanged();
            }
        }
    }

    private ResourceFolderType getFolderType() {
        IFile inputFile = getEditor().getInputFile();
        if (inputFile != null) {
            String folderName = inputFile.getParent().getName();
            return  ResourceFolderType.getFolderType(folderName);
        }
        return ResourceFolderType.ANIMATOR;
    }

    private void onDescriptorsChanged() {
        IStructuredModel model = getEditor().getModelForRead();
        if (model != null) {
            try {
                Node node = getEditor().getXmlDocument(model).getDocumentElement();
                getUiRootNode().reloadFromXmlNode(node);
            } finally {
                model.releaseFromRead();
            }
        }
    }
}