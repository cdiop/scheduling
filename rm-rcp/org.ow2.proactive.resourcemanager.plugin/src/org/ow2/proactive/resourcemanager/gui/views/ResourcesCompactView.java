/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed, Concurrent
 * computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis Contact:
 * proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this library; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Initial developer(s): The ProActive Team
 * http://proactive.inria.fr/team_members.htm Contributor(s):
 *
 * ################################################################
 */
package org.ow2.proactive.resourcemanager.gui.views;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.ow2.proactive.resourcemanager.gui.Activator;
import org.ow2.proactive.resourcemanager.gui.compact.CompactViewer;
import org.ow2.proactive.resourcemanager.gui.compact.Filter;


/**
 * Compact matrix view of resource pool.
 */
public class ResourcesCompactView extends ViewPart {

    public static final String ID = "org.ow2.proactive.resourcemanager.gui.views.ResourcesCompactView";
    private static CompactViewer compactView;
    private static ResourcesCompactView instance;

    public ResourcesCompactView() {
        instance = this;
    }

    public void createPartControl(Composite parent) {
        compactView = new CompactViewer(parent);

        compactView.init();
        hookContextMenu();
    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("PopupMenu");
        menuMgr.add(new Separator("top"));
        menuMgr.setRemoveAllWhenShown(true);
        Menu menu = menuMgr.createContextMenu(compactView.getComposite());
        compactView.getComposite().setMenu(menu);
        getSite().registerContextMenu(menuMgr, compactView);
    }

    /**
     * Called when view is closed
     * sacrifices tabViewer to garbage collector
     */
    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#dispose()
     */
    public void dispose() {
        super.dispose();
        compactView.dispose();
        compactView = null;
    }

    @Override
    public void setFocus() {
    }

    public static CompactViewer getCompactViewer() {
        return compactView;
    }

    public void putTreeViewOnTop() {
        try {
            this.getViewSite().getWorkbenchWindow().getActivePage().showView(ResourceExplorerView.ID);
        } catch (PartInitException e) {
            Activator.log(IStatus.ERROR, "An error occured. ", e);
        }
    }

    public static ResourcesCompactView getInstance() {
        return instance;
    }

    public void repaint(Filter filter) {
        compactView.repaint(filter);
    }
}