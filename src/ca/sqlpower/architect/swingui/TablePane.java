package ca.sqlpower.architect.swingui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.io.IOException;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.*;

public class TablePane extends JComponent implements SQLObjectListener, java.io.Serializable {

	private static final Logger logger = Logger.getLogger(TablePane.class);

	protected DragGestureListener dgl;
	protected DragGestureRecognizer dgr;
	protected DragSource ds;
	
	/**
	 * A constant indicating the title label on a TablePane.
	 */
	public static final int COLUMN_INDEX_TITLE = -1;

	/**
	 * A constant indicating no column or title.
	 */
	public static final int COLUMN_INDEX_NONE = -2;

	/**
	 * How many pixels should be left between the surrounding box and
	 * the column name labels.
	 */
	protected Insets margin = new Insets(1,1,1,1);

	protected DropTarget dt;

	static {
		UIManager.put(TablePaneUI.UI_CLASS_ID, "ca.sqlpower.architect.swingui.BasicTablePaneUI");
	}

	private SQLTable model;

	public TablePane(SQLTable m) {
		setModel(m);
		setMinimumSize(new Dimension(100,200));
		setPreferredSize(new Dimension(100,200));
		dt = new DropTarget(this, new TablePaneDropListener());

		dgl = new TablePaneDragGestureListener();
		ds = new DragSource();
		dgr = getToolkit().createDragGestureRecognizer(MouseDragGestureRecognizer.class, ds, this, DnDConstants.ACTION_MOVE, dgl);

		updateUI();
	}

	public void setUI(TablePaneUI ui) {super.setUI(ui);}

    public void updateUI() {
		setUI((TablePaneUI)UIManager.getUI(this));
		invalidate();
    }

    public String getUIClassID() {
        return TablePaneUI.UI_CLASS_ID;
    }

	// -------------------- sqlobject event support ---------------------

	/**
	 * Listens for property changes in the model (columns
	 * added).  If this change affects the appearance of
	 * this widget, we will notify all change listeners (the UI
	 * delegate) with a ChangeEvent.
	 */
	public void dbChildrenInserted(SQLObjectEvent e) {
		firePropertyChange("model.children", null, null);
		revalidate();
	}

	/**
	 * Listens for property changes in the model (columns
	 * removed).  If this change affects the appearance of
	 * this widget, we will notify all change listeners (the UI
	 * delegate) with a ChangeEvent.
	 */
	public void dbChildrenRemoved(SQLObjectEvent e) {
		firePropertyChange("model.children", null, null);
		revalidate();
	}

	/**
	 * Listens for property changes in the model (columns
	 * properties modified).  If this change affects the appearance of
	 * this widget, we will notify all change listeners (the UI
	 * delegate) with a ChangeEvent.
	 */
	public void dbObjectChanged(SQLObjectEvent e) {
		firePropertyChange("model."+e.getPropertyName(), null, null);
		revalidate();
	}

	/**
	 * Listens for property changes in the model (significant
	 * structure change).  If this change affects the appearance of
	 * this widget, we will notify all change listeners (the UI
	 * delegate) with a ChangeEvent.
	 */
	public void dbStructureChanged(SQLObjectEvent e) {
		firePropertyChange("model.children", null, null);
		revalidate();
	}

	// ----------------------- accessors and mutators --------------------------
	
	/**
	 * Gets the value of model
	 *
	 * @return the value of model
	 */
	public SQLTable getModel()  {
		return this.model;
	}

	/**
	 * Sets the value of model, removing this TablePane as a listener
	 * on the old model and installing it as a listener to the new
	 * model.
	 *
	 * @param argModel Value to assign to this.model
	 */
	public void setModel(SQLTable m) {
		SQLTable old = model;
        if (old != null) {
			old.removeSQLObjectListener(this);
		}

        if (m == null) {
			throw new IllegalArgumentException("model may not be null");
		} else {
            model = m;
		}
		model.addSQLObjectListener(this);
		setName("TablePanel: "+model.getShortDisplayName());

        firePropertyChange("model", old, model);
	}

	/**
	 * Gets the value of margin
	 *
	 * @return the value of margin
	 */
	public Insets getMargin()  {
		return this.margin;
	}

	/**
	 * Sets the value of margin
	 *
	 * @param argMargin Value to assign to this.margin
	 */
	public void setMargin(Insets argMargin) {
		Insets old = margin;
		this.margin = (Insets) argMargin.clone();
		firePropertyChange("margin", old, margin);
		revalidate();
	}

	// ------------------ utility methods ---------------------

	/**
	 * Returns the index of the column that point p is on top of.  If
	 * p is on top of the table name, returns COLUMN_INDEX_TITLE.
	 * Otherwise, p is not over a column or title and the returned
	 * index is COLUMN_INDEX_NONE.
	 */
	public int pointToColumnIndex(Point p) throws ArchitectException {
		return ((TablePaneUI) ui).pointToColumnIndex(p);
	}

	/**
	 * Tracks incoming objects and adds successfully dropped objects
	 * at the current mouse position.
	 */
	public static class TablePaneDropListener implements DropTargetListener {

		/**
		 * Called while a drag operation is ongoing, when the mouse
		 * pointer enters the operable part of the drop site for the
		 * DropTarget registered with this listener.
		 */
		public void dragEnter(DropTargetDragEvent dtde) {
			dragOver(dtde);
		}
		
		/**
		 * Called while a drag operation is ongoing, when the mouse
		 * pointer has exited the operable part of the drop site for the
		 * DropTarget registered with this listener.
		 */
		public void dragExit(DropTargetEvent dte) {
		}
		
		/**
		 * Called when a drag operation is ongoing, while the mouse
		 * pointer is still over the operable part of the drop site for
		 * the DropTarget registered with this listener.
		 */
		public void dragOver(DropTargetDragEvent dtde) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY);
		}
		
		/**
		 * Called when the drag operation has terminated with a drop on
		 * the operable part of the drop site for the DropTarget
		 * registered with this listener.
		 */
		public void drop(DropTargetDropEvent dtde) {
			Transferable t = dtde.getTransferable();
			TablePane c = (TablePane) dtde.getDropTargetContext().getComponent();
			DataFlavor importFlavor = bestImportFlavor(c, t.getTransferDataFlavors());
			if (importFlavor == null) {
				dtde.rejectDrop();
			} else {
				try {
					Object someData = t.getTransferData(importFlavor);
					logger.debug("drop: got object of type "+someData.getClass().getName());
					if (someData instanceof SQLTable) {
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						c.getModel().inherit((SQLTable) someData);
						dtde.dropComplete(true);
						return;
					} else if (someData instanceof SQLColumn) {
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						SQLColumn column = (SQLColumn) someData;
						c.getModel().addColumn(column);
						logger.debug("Added "+column.getColumnName()+" to table");
						dtde.dropComplete(true);
						return;
					} else if (someData instanceof SQLObject[]) {
						// needs work (should use addSchema())
						dtde.acceptDrop(DnDConstants.ACTION_COPY);
						SQLObject[] objects = (SQLObject[]) someData;
						for (int i = 0; i < objects.length; i++) {
							if (objects[i] instanceof SQLColumn) {
								c.getModel().addColumn((SQLColumn) objects[i]);
							}
						}
						dtde.dropComplete(true);
						return;
					} else {
						dtde.rejectDrop();
					}
				} catch (UnsupportedFlavorException ufe) {
					ufe.printStackTrace();
					dtde.rejectDrop();
				} catch (IOException ioe) {
					ioe.printStackTrace();
					dtde.rejectDrop();
				} catch (InvalidDnDOperationException ex) {
					ex.printStackTrace();
					dtde.rejectDrop();
				} catch (ArchitectException ex) {
					ex.printStackTrace();
					dtde.rejectDrop();
				}
			}
		}
		
		/**
		 * Called if the user has modified the current drop gesture.
		 */
		public void dropActionChanged(DropTargetDragEvent dtde) {
		}

		/**
		 * Chooses the best import flavour from the flavors array for
		 * importing into c.  The current implementation actually just
		 * chooses the first acceptable flavour.
		 *
		 * @return The first acceptable DataFlavor in the flavors
		 * list, or null if no acceptable flavours are present.
		 */
		public DataFlavor bestImportFlavor(JComponent c, DataFlavor[] flavors) {
			logger.debug("can I import "+Arrays.asList(flavors));
 			for (int i = 0; i < flavors.length; i++) {
				String cls = flavors[i].getDefaultRepresentationClassAsString();
				logger.debug("representation class = "+cls);
				logger.debug("mime type = "+flavors[i].getMimeType());
				logger.debug("type = "+flavors[i].getPrimaryType());
				logger.debug("subtype = "+flavors[i].getSubType());
				logger.debug("class = "+flavors[i].getParameter("class"));
				logger.debug("isSerializedObject = "+flavors[i].isFlavorSerializedObjectType());
				logger.debug("isInputStream = "+flavors[i].isRepresentationClassInputStream());
				logger.debug("isRemoteObject = "+flavors[i].isFlavorRemoteObjectType());
				logger.debug("isLocalObject = "+flavors[i].getMimeType().equals(DataFlavor.javaJVMLocalObjectMimeType));


 				if (flavors[i].equals(SQLObjectTransferable.flavor)
					|| flavors[i].equals(SQLObjectListTransferable.flavor)) {
					logger.debug("YES");
 					return flavors[i];
				}
 			}
			logger.debug("NO!");
 			return null;
		}

		/**
		 * This is set up this way because this DropTargetListener was
		 * derived from a TransferHandler.  It works, so no sense in
		 * changing it.
		 */
		public boolean canImport(JComponent c, DataFlavor[] flavors) {
			return bestImportFlavor(c, flavors) != null;
		} 
	}

	public static class TablePaneDragGestureListener implements DragGestureListener {
		public void dragGestureRecognized(DragGestureEvent dge) {
			TablePane tp = (TablePane) dge.getComponent();
			int colIndex = COLUMN_INDEX_NONE;
			try {
				colIndex = tp.pointToColumnIndex(dge.getDragOrigin());
			} catch (ArchitectException e) {
				logger.error("Got exception while translating drag point", e);
			}
			logger.debug("Recognized drag gesture! col="+colIndex);
			if (colIndex == COLUMN_INDEX_TITLE) {
				// move the tablepane around
			} else if (colIndex >= 0) {
				// export column as DnD event
				logger.error("Dragging columns is not implemented yet");
			}
		}
	}
}
