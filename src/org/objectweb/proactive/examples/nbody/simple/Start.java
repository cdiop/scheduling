package org.objectweb.proactive.examples.nbody.simple;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.examples.nbody.common.Displayer;
import org.objectweb.proactive.examples.nbody.common.Planet;
import org.objectweb.proactive.examples.nbody.common.Rectangle;


public class Start {
    
    public static void main(String[] args) {  
        org.objectweb.proactive.examples.nbody.common.Start.main(args) ; 
    }
    
    public static void main(int totalNbBodies, int maxIter, Displayer displayer, Node[] nodes,
            org.objectweb.proactive.examples.nbody.common.Start killsupport) {
        System.out.println("RUNNING simplest VERSION");
        
        Rectangle universe = new Rectangle(-100 , -100 , 100 , 100);
        Domain [] domainArray = new Domain [totalNbBodies];
        for (int  i = 0 ; i < totalNbBodies ; i++) {
            Object [] constructorParams = new Object [] {
                    new Integer(i), 
                    new Planet (universe)
            	};
            try {
                // Create all the Domains used in the simulation 
                domainArray[i] = (Domain) ProActive.newActive(
                        Domain.class.getName(), 
                        constructorParams, 
                        nodes[(i+1) % nodes.length]
                );
            }
            catch (ActiveObjectCreationException e) { killsupport.abort(e); } 
            catch (NodeException e) { killsupport.abort(e); }
        }
        
        System.out.println("[NBODY] " + totalNbBodies + " Planets are deployed");
        
        // Create a maestro, which will orchestrate the whole simulation, synchronizing the computations of the Domains
        Maestro maestro = null;
        try {
            maestro = (Maestro) ProActive.newActive (
                    Maestro.class.getName(), 
                    new Object[] {domainArray, new Integer(maxIter), killsupport} , 
                    nodes[0]
            );
        } 
        catch (ActiveObjectCreationException e) { killsupport.abort(e); } 
        catch (NodeException e) { killsupport.abort(e); }
        
        // init workers
        for (int i=0 ; i < totalNbBodies ; i ++)
            domainArray[i].init(domainArray, displayer, maestro);
        
    }
    
}
