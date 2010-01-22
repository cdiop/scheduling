/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2010 INRIA/University of 
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.scheduler.ext.scilab.embedded;

import javasci.SciStringMatrix;
import org.apache.log4j.Logger;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.ow2.proactive.scheduler.common.exception.SchedulerException;
import org.ow2.proactive.scheduler.common.job.JobPriority;
import org.ow2.proactive.scheduler.common.util.SchedulerLoggers;

import javax.security.auth.login.LoginException;
import java.util.ArrayList;


/**
 * This class is a Java object handling the interaction between Scilab c/jni code and the ActiveObject AOScilabEnvironment
 * 
 * @author The ProActive Team
 */
public class ScilabSolver {

    protected static Logger logger = ProActiveLogger.getLogger(SchedulerLoggers.SCILAB);

    private static AOScilabEnvironment scilabSolver;

    static {
        scilabSolver = null;
    }

    public static String[][] solve(String[] inputScripts, String functionsDefinition, String mainScript,
            String scriptURL, int priority, int debugVal) throws Throwable {
        boolean debug = debugVal > 0;
        if (debug) {
            System.out.println("[ScilabSolver] In Solver");
        }
        String[][] results = null;
        ArrayList<ResultsAndLogs> sciResults;
        sciResults = scilabSolver.solve(inputScripts, functionsDefinition, mainScript, null, JobPriority
                .findPriority(priority), debug);
        sciResults = (ArrayList<ResultsAndLogs>) PAFuture.getFutureValue(sciResults);

        if (sciResults != null) {
            if (debug) {
                System.out.println(sciResults);
                System.out.println("[ScilabSolver] Solved");
            }

            results = new String[3][sciResults.size()];

            for (int i = 0; i < sciResults.size(); i++) {
                if (((SciStringMatrix) sciResults.get(i).getResult()).getNbRow() > 0) {
                    results[0][i] = ((SciStringMatrix) sciResults.get(i).getResult()).getData()[0];
                } else {
                    results[0][i] = "output = %f";
                }
                results[1][i] = sciResults.get(i).getLogs();
                if (sciResults.get(i).getException() != null) {
                    results[2][i] = sciResults.get(i).getException().getMessage();
                    throw sciResults.get(i).getException();
                } else {
                    results[2][i] = null;
                }

            }
        } else {
            System.out.println("[ScilabSolver] Solve returned NULL...");
        }
        return results;
    }

    public static void createConnection(String url, String login, String passwd) throws SchedulerException,
            LoginException {
        if (scilabSolver == null) {

            try {
                scilabSolver = (AOScilabEnvironment) PAActiveObject.newActive(AOScilabEnvironment.class
                        .getName(), new Object[] {});
            } catch (ActiveObjectCreationException e) {
                System.out.println("[ScilabSolver] Error Creating AOScilabEnvironment AO..");
                e.printStackTrace();
            } catch (NodeException e) {
                System.out.println("[ScilabSolver] Error Connecting to Scheduler..");
                e.printStackTrace();
            }
        }

        scilabSolver.join(url);

        scilabSolver.startLogin();

        while (!scilabSolver.isLoggedIn()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
