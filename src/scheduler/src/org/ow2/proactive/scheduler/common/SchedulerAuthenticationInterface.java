/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
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
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.common;

import javax.security.auth.login.LoginException;

import org.objectweb.proactive.annotation.PublicAPI;
import org.ow2.proactive.authentication.Authentication;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.scheduler.common.exception.SchedulerException;


/**
 * Scheduler Authentication Interface provides method to connect to the scheduler.<br>
 * Before using the scheduler communication interface {@link org.ow2.proactive.scheduler.core.UserScheduler}, you have to connect it using a login/password using this interface.<br>
 * You can get this interface by using the scheduler connection {@link SchedulerConnection}
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
@PublicAPI
public interface SchedulerAuthenticationInterface extends Authentication {

    /**
     * Connect the user interface to a scheduler with the given scheduler URL.<br>
     * If the login or/and password do not match an allowed one,
     * it will throw an LoginException.<br>
     * If the authentication succeed, it will return a new scheduler user API to managed job.<br>
     * Note that you can use the provided {@link SchedulerAuthenticationGUIHelper} class to display a
     * graphical interface that will ask the URL, login and password.
     *
     * @param user the user name of the user to connect.
     * @param password the password of the user to connect.
     * @return The {@link org.ow2.proactive.scheduler.core.UserScheduler} interface if this user can access to the scheduler.
     * @throws LoginException thrown if this user/password does not match any entries.
     * @throws SchedulerException thrown if the connection to the scheduler cannot be established.
     */
    @Deprecated
    public UserSchedulerInterface logAsUser(String user, String password) throws LoginException;

    /**
     * Connect the administrator interface to a scheduler with the given scheduler URL.<br>
     * If the login or/and password do not match an allowed one,
     * it will throw an LoginException.<br>
     * If the authentication succeed, it will return a new scheduler administrator API.<br>
     * This authentication requires that the user has administrator rights.<br>
     * Note that you can use the provided {@link SchedulerAuthenticationGUIHelper} class to display a
     * graphical interface that will ask the URL, login and password.
     *
     * @param user the user name of the user to connect.
     * @param password the password of the user to connect.
     * @return The {@link org.ow2.proactive.scheduler.core.UserScheduler} interface if this user can access to the scheduler.
     * @throws LoginException thrown if this user/password does not match any entries.
     * @throws SchedulerException thrown if the connection to the scheduler cannot be established.
     */
    @Deprecated
    public AdminSchedulerInterface logAsAdmin(String user, String password) throws LoginException;

    /**
     * Connect the user interface to a scheduler with the given scheduler URL,
     * sending login and password in an encrypted state.<br>
     * If the login or/and password do not match an allowed one, it will throw
     * an LoginException.<br>
     * If the authentication succeed, it will return a new scheduler user API to
     * managed job.<br>
     * Note that you can use the provided
     * {@link SchedulerAuthenticationGUIHelper} class to display a graphical
     * interface that will ask the URL, login and password.
     * 
     * @param cred Object encapsulating encrypted credentials, and information on how to decrypt them
     * @return The {@link org.ow2.proactive.scheduler.core.UserScheduler} interface if this user can access to the scheduler.
     * @throws LoginException thrown if this user/password does not match any entries.
     * @throws SchedulerException thrown if the connection to the scheduler cannot be established.
     */
    public UserSchedulerInterface logAsUser(Credentials cred) throws LoginException;

    /**
     * Connect the administrator interface to a scheduler with the given
     * scheduler URL, sending login and password in an encrypted state.<br>
     * If the login or/and password do not match an allowed one, it will throw
     * an LoginException.<br>
     * If the authentication succeed, it will return a new scheduler
     * administrator API.<br>
     * This authentication requires that the user has administrator rights.<br>
     * Note that you can use the provided
     * {@link SchedulerAuthenticationGUIHelper} class to display a graphical
     * interface that will ask the URL, login and password.
     * 
     * @param cred Object encapsulating encrypted credentials, and information on how to decrypt them
     * @return The {@link org.ow2.proactive.scheduler.core.UserScheduler} interface if this user can access to the scheduler.
     * @throws LoginException thrown if this user/password does not match any entries.
     * @throws SchedulerException thrown if the connection to the scheduler cannot be established.
     */
    public AdminSchedulerInterface logAsAdmin(Credentials cred) throws LoginException;
}
