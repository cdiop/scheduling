/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2015 INRIA/University of
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
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package functionaltests.scripts.selection;

import functionaltests.utils.SchedulerFunctionalTestWithRestart;
import org.junit.Test;

import java.io.File;
import java.net.URL;


/**
 * This test checks that variable bindings are available and correctly set in selection scripts
 * The test timeouts if they are not, debugging can be done by reading the scheduler logs
 */
public class TestJobSelScriptVariables extends SchedulerFunctionalTestWithRestart {

    private static URL jobDescriptor = TestJobSelScriptVariables.class
            .getResource("/functionaltests/descriptors/Job_selection_script_variables.xml");

    @Test(timeout = 120000)
    public void testJobSelScriptVariables() throws Throwable {
        schedulerHelper.testJobSubmission(new File(jobDescriptor.toURI())
                .getAbsolutePath());
        schedulerHelper.checkNodesAreClean();
    }

}
