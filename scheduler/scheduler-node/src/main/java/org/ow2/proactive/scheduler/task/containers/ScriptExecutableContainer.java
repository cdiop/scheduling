/*
 *  *
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
 *  Contributor(s):
 *
 *  * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.task.containers;

import org.ow2.proactive.scheduler.common.task.util.ByteArrayWrapper;
import org.ow2.proactive.scripting.Script;
import org.ow2.proactive.scripting.TaskScript;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * Reuse the Java executable container as part of a forked task, specialized for a script execution.
 * @author The ProActive Team
 * @since ProActive Scheduling 3.4
 */
public class ScriptExecutableContainer extends ExecutableContainer {

    /** Arguments of the task as a map */
    protected final Map<String, ByteArrayWrapper> serializedArguments = new HashMap<>();

    private final TaskScript script;

    public ScriptExecutableContainer(TaskScript script) {
        this.script = script;
    }

    public Script<Serializable> getScript() {
        return script;
    }

    public Map<String, ByteArrayWrapper> getSerializedArguments() {
        return serializedArguments;
    }

}
