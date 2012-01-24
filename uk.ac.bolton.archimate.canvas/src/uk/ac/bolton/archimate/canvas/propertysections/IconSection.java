/*******************************************************************************
 * Copyright (c) 2011 Bolton University, UK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 *******************************************************************************/
package uk.ac.bolton.archimate.canvas.propertysections;

import java.io.File;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.gef.EditPart;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PlatformUI;

import uk.ac.bolton.archimate.canvas.model.ICanvasPackage;
import uk.ac.bolton.archimate.canvas.model.IIconic;
import uk.ac.bolton.archimate.editor.model.IArchiveManager;
import uk.ac.bolton.archimate.editor.model.commands.EObjectFeatureCommand;
import uk.ac.bolton.archimate.editor.propertysections.DiagramModelImageSection;
import uk.ac.bolton.archimate.editor.propertysections.ITabbedLayoutConstants;
import uk.ac.bolton.archimate.editor.ui.ImageFactory;
import uk.ac.bolton.archimate.model.IArchimatePackage;
import uk.ac.bolton.archimate.model.ILockable;


/**
 * Property Section for an Icon
 * 
 * @author Phillip Beauvoir
 */
public class IconSection extends DiagramModelImageSection {
    
    /**
     * Filter to show or reject this section depending on input value
     */
    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return (object instanceof EditPart) && ((EditPart)object).getModel() instanceof IIconic;
        }
    }

    /*
     * Adapter to listen to changes made elsewhere (including Undo/Redo commands)
     */
    private Adapter eAdapter = new AdapterImpl() {
        @Override
        public void notifyChanged(Notification msg) {
            Object feature = msg.getFeature();
            // Model event
            if(feature == IArchimatePackage.Literals.DIAGRAM_MODEL_IMAGE_PROVIDER__IMAGE_PATH) {
                refreshPreviewImage();
            }
            else if(feature == IArchimatePackage.Literals.LOCKABLE__LOCKED
                    || feature == ICanvasPackage.Literals.ICONIC__IMAGE_POSITION) {
                refreshButtons();
            }
        }
    };
    
    private IIconic fIconic;
    
    private Image fImage;
    private Canvas fCanvas;
    private Combo fComboPosition;
    
    private static final String[] fComboPositionItems = {
        "Top Left",
        "Top Centre",
        "Top Right",
        "Middle Left",
        "Middle Centre",
        "Middle Right",
        "Bottom Left",
        "Bottom Centre",
        "Bottom Right",
    };
    
    
    @Override
    protected void createControls(Composite parent) {
        createCLabel(parent, "Preview:", ITabbedLayoutConstants.STANDARD_LABEL_WIDTH, SWT.NONE);
        
        final int canvasSize = IIconic.MAX_IMAGE_SIZE;
        
        fCanvas = new Canvas(parent, SWT.BORDER);
        getWidgetFactory().adapt(fCanvas);
        GridData gd = new GridData(SWT.NONE, SWT.NONE, false, false);
        gd.widthHint = canvasSize;
        gd.heightHint = canvasSize;
        fCanvas.setLayoutData(gd);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        fCanvas.setLayout(layout);
        
        fCanvas.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                disposeImage();
            }
        });
        
        fCanvas.addListener(SWT.MouseDoubleClick, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if(fIconic instanceof ILockable && ((ILockable)fIconic).isLocked()) {
                    return;
                }
                chooseImage();
            }
        });
        
        fCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                if(fImage != null) {
                    Rectangle bounds = fImage.getBounds();
                    int x = (canvasSize - bounds.width) / 2;
                    int y = (canvasSize - bounds.height) / 2;
                    e.gc.drawImage(fImage, x, y);
                }
            }
        });
        
        String tooltip = "Drag an image onto here or double-click to select";
        fCanvas.setToolTipText(tooltip);
        
        DropTarget target = new DropTarget(fCanvas, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
        target.setTransfer(new Transfer[] { FileTransfer.getInstance() } );
        
        target.addDropListener(new DropTargetAdapter() {
            @Override
            public void drop(DropTargetEvent event) {
                if(event.data instanceof String[]) {
                    if(fIconic instanceof ILockable && ((ILockable)fIconic).isLocked()) {
                        return;
                    }
                    File file = new File(((String[])event.data)[0]);
                    setImage(file);
                }
            }
        });
        
        // Image Button
        createImageButton(parent);
        
        // Position
        createCLabel(parent, "Position:", ITabbedLayoutConstants.STANDARD_LABEL_WIDTH, SWT.NONE);

        fComboPosition = new Combo(parent, SWT.READ_ONLY);
        fComboPosition.setItems(fComboPositionItems);
        gd = new GridData(SWT.NONE, SWT.NONE, false, false);
        gd.widthHint = 150;
        fComboPosition.setLayoutData(gd);
        fComboPosition.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if(isAlive()) {
                    fIsExecutingCommand = true;
                    getCommandStack().execute(new EObjectFeatureCommand("Icon Position",
                                                fIconic,
                                                ICanvasPackage.Literals.ICONIC__IMAGE_POSITION,
                                                fComboPosition.getSelectionIndex()));
                    fIsExecutingCommand = false;
                }
            }
        });
        
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, HELP_ID);
    }
    
    @Override
    protected void setElement(Object element) {
        if(element instanceof EditPart && ((EditPart)element).getModel() instanceof IIconic) {
            fIconic = (IIconic)((EditPart)element).getModel();
        }

        if(fIconic == null) {
            throw new RuntimeException("Object was null");
        }
        
        refreshControls();
    }
    
    @Override
    protected void refreshControls() {
        refreshPreviewImage();
        refreshButtons();
    }
    
    protected void refreshPreviewImage() {
        disposeImage();
        
        if(fIconic.getImagePath() != null) {
            IArchiveManager archiveManager = (IArchiveManager)fIconic.getAdapter(IArchiveManager.class);
            
            Image image = null;
            try {
                image = archiveManager.createImage(fIconic.getImagePath());
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
            
            if(image != null) {
                // If the image is bigger than the maximum allowed image then create a scaled image
                if(image.getBounds().width > IIconic.MAX_IMAGE_SIZE || image.getBounds().height > IIconic.MAX_IMAGE_SIZE) {
                    fImage = ImageFactory.getScaledImage(image, IIconic.MAX_IMAGE_SIZE);
                    image.dispose();
                }
                // Else use original
                else {
                    fImage = image;
                }
            }
        }
        
        fCanvas.redraw();
    }
    
    @Override
    protected void refreshButtons() {
        boolean enabled = fIconic instanceof ILockable ? !((ILockable)fIconic).isLocked() : true;
        
        fImageButton.setEnabled(enabled);
        
        int position = fIconic.getImagePosition();
        if(position < IIconic.ICON_POSITION_TOP_LEFT || position > IIconic.ICON_POSITION_BOTTOM_RIGHT) {
            position = IIconic.ICON_POSITION_TOP_RIGHT;
        }
        
        if(!fIsExecutingCommand) {
            fComboPosition.select(position);
            fComboPosition.setEnabled(enabled);
        }
    }
    
    @Override
    protected Adapter getECoreAdapter() {
        return eAdapter;
    }

    @Override
    protected IIconic getEObject() {
        return fIconic;
    }
    
    protected void disposeImage() {
        if(fImage != null && !fImage.isDisposed()) {
            fImage.dispose();
            fImage = null;
        }
    }
}
