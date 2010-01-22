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
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.authentication;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.objectweb.proactive.Body;
import org.objectweb.proactive.RunActive;
import org.objectweb.proactive.Service;
import org.objectweb.proactive.core.UniqueID;
import org.objectweb.proactive.core.body.proxy.BodyProxy;
import org.objectweb.proactive.core.body.request.Request;
import org.objectweb.proactive.core.mop.Proxy;
import org.objectweb.proactive.core.mop.StubObject;


/**
 * Class represents an active object with restricted access. It accepts and serves requests only from
 * objects which had been registered as trusted services.
 * 
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9.1
 */
public abstract class RestrictedService implements Loggable, RunActive {

    /** public methods */
    private Set<String> publicMethods = new TreeSet<String>();
    /** trusted services */
    private HashMap<UniqueID, Boolean> trustedServices = new HashMap<UniqueID, Boolean>();
    /** class logger */
    private Logger logger = getLogger();

    /**
     * Performs filtering of requests.
     *
     * @param body an active object body
     */
    public void runActivity(Body body) {
        Service service = new Service(body);

        while (body.isActive()) {
            service.waitForRequest();

            Request request = service.blockingRemoveOldest();
            if (request.getSender().equals(body) || trustedServices.containsKey(request.getSourceBodyID()) ||
                publicMethods.contains(request.getMethodName())) {
                service.serve(request);
            } else {
                logger.debug("Unauthorized request " + request.getMethodName() + " from " +
                    request.getSender() + " to class " + this.getClass().getName());
                continue;
            }
        }
    }

    /**
     * Intended to be used for immediate services filtering
     *
     * @param id unique id of the caller
     *
     * @return true if id corresponds to registered caller
     */
    protected boolean trustedImmediateServiceCaller(UniqueID id) {
        return trustedServices.containsKey(id);
    }

    /**
     * Registers a trusted service. Body id of caller active objects is used as request identifier.
     *
     * @param service an active object to register
     */
    protected void registerTrustedService(Object service) {
        UniqueID id = getBodyId(service);
        logger.debug("Trying to register service " + service.getClass().getName() + " with id " + id);
        registerTrustedService(id);
    }

    /**
     * Unregisters a trusted service.
     *
     * @param service an active object to unregister
     */
    protected void unregisterTrustedService(Object service) {
        UniqueID id = getBodyId(service);
        logger.debug("Trying to unregister service " + service.getClass().getName() + " with id " + id);
        unregisterTrustedService(id);
    }

    /**
     * Registers a trusted service with a given id.
     *
     * @param id of the caller body
     * @return true if registration succeed
     */
    protected boolean registerTrustedService(UniqueID id) {
        if (id != null && !trustedServices.containsKey(id)) {
            trustedServices.put(id, true);
            logger.debug("Trusted service registred: id " + id + " for " + this.getClass().getName());
            return true;
        } else {
            logger.debug("Cannot register trusted service with id " + id + " for " +
                this.getClass().getName());
            return false;
        }
    }

    /**
     * Unregisters a trusted service with a given id.
     *
     * @param id of the caller body
     */
    protected void unregisterTrustedService(UniqueID id) {

        if (id != null && trustedServices.containsKey(id)) {
            trustedServices.remove(id);
            logger.debug("Trusted service unregistred: id " + id + " in " + this.getClass().getName());
        } else {
            logger.debug("Cannot unregister trusted service with id " + id + " in " +
                this.getClass().getName());
        }
    }

    /**
     * Extract id from active object.
     * TODO find more straightforward way to do that
     *
     * @param service a target active object
     * @return an active object body id
     */
    private UniqueID getBodyId(Object service) {

        if (service instanceof StubObject && ((StubObject) service).getProxy() != null) {
            Proxy proxy = ((StubObject) service).getProxy();

            if (proxy instanceof BodyProxy) {
                return ((BodyProxy) proxy).getBodyID();
            }
        }

        return null;
    }

    /**
     * Declares public method for restricted service. Accessible for all objects.
     * Use case: callback to resource manager during GCM deployment
     *
     * @param methodName name of the public method
     */
    protected void setPublicMethod(String methodName) {
        publicMethods.add(methodName);
    }
}
