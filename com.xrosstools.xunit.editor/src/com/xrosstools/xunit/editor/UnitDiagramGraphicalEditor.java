package com.xrosstools.xunit.editor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.EventObject;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.RootEditPart;
import org.eclipse.gef.commands.CommandStackListener;
import org.eclipse.gef.dnd.TemplateTransferDragSourceListener;
import org.eclipse.gef.editparts.ScalableFreeformRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.ContentOutlinePage;
import org.eclipse.gef.ui.parts.GraphicalEditorWithPalette;
import org.eclipse.gef.ui.parts.TreeViewer;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import com.xrosstools.common.XmlHelper;
import com.xrosstools.xunit.editor.io.UnitNodeDiagramFactory;
import com.xrosstools.xunit.editor.model.UnitNodeDiagram;
import com.xrosstools.xunit.editor.parts.UnitNodePartFactory;
import com.xrosstools.xunit.editor.treeparts.UnitNodeTreePartFactory;

public class UnitDiagramGraphicalEditor extends GraphicalEditorWithPalette {
    private UnitNodeDiagram diagram;
    private PaletteRoot paletteRoot;
    
    private UnitNodeDiagramFactory diagramFactory = new UnitNodeDiagramFactory();
    
    private CommandStackListener commandStackListener = new CommandStackListener() {
        public void commandStackChanged(EventObject event) {
                firePropertyChange(PROP_DIRTY);
        }
    };

    public UnitDiagramGraphicalEditor() {
        setEditDomain(new DefaultEditDomain(this));
    }
    
    protected void configureGraphicalViewer() {
    	ScalableFreeformRootEditPart root = new ScalableFreeformRootEditPart();
        super.configureGraphicalViewer();
        getGraphicalViewer().setRootEditPart(root);
        getGraphicalViewer().setEditPartFactory(new UnitNodePartFactory());
        getGraphicalViewer().setContextMenu(new UnitContextMenuProvider(getGraphicalViewer(), this));
        initActions(root);
        getCommandStack().addCommandStackListener(commandStackListener);
    }
    
    private void initActions(ScalableFreeformRootEditPart root){
    	IAction action = new ZoomInAction(root.getZoomManager());
    	getActionRegistry().registerAction(action);
    	getSite().getKeyBindingService().registerAction(action);
    	action = new ZoomOutAction(root.getZoomManager());
    	getActionRegistry().registerAction(action);
    	getSite().getKeyBindingService().registerAction(action);
    }
    
    public RootEditPart getRootEditPart(){
    	return getGraphicalViewer().getRootEditPart();
    }
    
    public UnitNodeDiagram getModel(){
    	return diagram;
    }

    protected void initializeGraphicalViewer() {
        getGraphicalViewer().setContents(diagram);
    }

    public void doSave(IProgressMonitor monitor) {
		try {
			IFile file = ((IFileEditorInput)getEditorInput()).getFile();
			file.setContents(new ByteArrayInputStream(writeAsXML().getBytes()), 
							true, false, monitor);
			getCommandStack().markSaveLocation();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private String writeAsXML() throws Exception{
        return XmlHelper.format(diagramFactory.writeToDocument(diagram));
    }

    public void doSaveAs() {
    	SaveAsDialog dialog= new SaveAsDialog(getSite().getWorkbenchWindow().getShell());
    	dialog.setOriginalFile(((IFileEditorInput)getEditorInput()).getFile());
    	dialog.open();
    	IPath path= dialog.getResult();
    	
    	if (path == null)
    		return;
    	
    	IWorkspace workspace= ResourcesPlugin.getWorkspace();
    	final IFile file= workspace.getRoot().getFile(path);
    	
    	WorkspaceModifyOperation op= new WorkspaceModifyOperation() {
    		public void execute(final IProgressMonitor monitor) throws CoreException {
    			try {
    				file.create(new ByteArrayInputStream(writeAsXML().getBytes("utf-8")), true, monitor);
    			} 
    			catch (Exception e) {
    				e.printStackTrace();
    			}
    		}
    	};
    	
    	try {
    		new ProgressMonitorDialog(getSite().getWorkbenchWindow().getShell()).run(false, true, op);
    		setInput(new FileEditorInput((IFile)file));
    		getCommandStack().markSaveLocation();
    	} 
    	catch (Exception e) {
    		e.printStackTrace();
    	} 
    }
    
	public boolean isDirty() {
		return getCommandStack().isDirty();
	}


    public boolean isSaveAsAllowed() {
        return true;
    }

    protected void setInput(IEditorInput input) {
        super.setInput(input);
    	FileEditorInput fInput = (FileEditorInput)getEditorInput();
    	setPartName(fInput.getName());

    	IFile file = ((IFileEditorInput)input).getFile();
    	try {
    		InputStream is = file.getContents(false);
    		diagram = getFromXML(is);
    		is.close();
    	} catch (Exception e) {
    		e.printStackTrace();
    		diagram = diagramFactory.getEmptyDiagram();
    	}
    }
    
    private UnitNodeDiagram getFromXML(InputStream is) throws Exception {
    	return diagramFactory.getFromDocument(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is));
    }

    public Object getAdapter(Class type) {
        if (type == IContentOutlinePage.class)
            return new OutlinePage();
        if (type == ZoomManager.class)
        	return getGraphicalViewer().getProperty(ZoomManager.class.toString());
        return super.getAdapter(type);
    }

    protected PaletteRoot getPaletteRoot() {
        if (this.paletteRoot == null) {
            this.paletteRoot = new UnitPaletteFactory().createPalette();
        }
        return this.paletteRoot;
    }

    protected void initializePaletteViewer() {
        super.initializePaletteViewer();
        getPaletteViewer().addDragSourceListener(new TemplateTransferDragSourceListener(getPaletteViewer()));
    }
    
    private class OutlinePage extends ContentOutlinePage {
        private Control outline;
        public OutlinePage() {
            super(new TreeViewer());
        }
        public void createControl(Composite parent) {
            outline = getViewer().createControl(parent);
            getSelectionSynchronizer().addViewer(getViewer());
            getViewer().setEditDomain(getEditDomain());
            getViewer().setEditPartFactory(new UnitNodeTreePartFactory());
            getViewer().setContents(diagram);
        }

        public Control getControl() {
            return outline;
        }

        public void dispose() {
            getSelectionSynchronizer().removeViewer(getViewer());
            super.dispose();
        }
    }
}
