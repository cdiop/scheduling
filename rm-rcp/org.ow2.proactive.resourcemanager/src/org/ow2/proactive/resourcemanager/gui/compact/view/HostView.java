/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.resourcemanager.gui.compact.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.ow2.proactive.resourcemanager.Activator;
import org.ow2.proactive.resourcemanager.gui.Internal;
import org.ow2.proactive.resourcemanager.gui.compact.Filter;
import org.ow2.proactive.resourcemanager.gui.compact.LabelMouseListener;
import org.ow2.proactive.resourcemanager.gui.data.model.TreeLeafElement;
import org.ow2.proactive.resourcemanager.gui.data.model.TreeParentElement;
import org.ow2.proactive.resourcemanager.gui.views.ResourcesCompactView;


/**
 *
 * Graphical representation of the host.
 *
 */
public class HostView extends View {

    private boolean virtualHost = false;

    public HostView(TreeLeafElement element, Filter filter) {
        super(element);

        if (filter.showHosts) {
            label = new Label(ResourcesCompactView.getCompactViewer().getComposite(), SWT.SHADOW_NONE);
            label.setBackground(ResourcesCompactView.getCompactViewer().getComposite().getBackground());

            // checking if the first node of this host has "virt-" in its url
            TreeLeafElement[] vms = ((TreeParentElement) element).getChildren();
            if (vms != null && vms.length > 0) {
                TreeLeafElement[] nodes = ((TreeParentElement) vms[0]).getChildren();
                if (nodes != null && nodes.length > 0 &&
                    nodes[0].getName().toLowerCase().contains(Internal.VIRT_PREFIX)) {
                    virtualHost = true;
                }
            }
            if (virtualHost) {
                label.setImage(Activator.getDefault().getImageRegistry().get(Internal.IMG_HOST_VIRT));
            } else {
                label.setImage(Activator.getDefault().getImageRegistry().get(Internal.IMG_HOST));
            }
            label.setToolTipText(toString());
            label.addMouseListener(new LabelMouseListener(this));
        }
    }

    @Override
    public String toString() {
        return (virtualHost ? "VM: " : "Host: ") + element.getName();
    }
}
