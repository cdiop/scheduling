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
 *  * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.task.data;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.extensions.dataspaces.Utils;
import org.objectweb.proactive.extensions.dataspaces.api.DataSpacesFileObject;
import org.objectweb.proactive.extensions.dataspaces.api.FileSelector;
import org.objectweb.proactive.extensions.dataspaces.api.FileType;
import org.objectweb.proactive.extensions.dataspaces.api.PADataSpaces;
import org.objectweb.proactive.extensions.dataspaces.core.DataSpacesNodes;
import org.objectweb.proactive.extensions.dataspaces.core.InputOutputSpaceConfiguration;
import org.objectweb.proactive.extensions.dataspaces.core.SpaceInstanceInfo;
import org.objectweb.proactive.extensions.dataspaces.core.naming.NamingService;
import org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException;
import org.objectweb.proactive.extensions.dataspaces.exceptions.SpaceAlreadyRegisteredException;
import org.objectweb.proactive.utils.NamedThreadFactory;
import org.objectweb.proactive.utils.OperatingSystem;
import org.objectweb.proactive.utils.StackTraceUtil;
import org.ow2.proactive.resourcemanager.nodesource.dataspace.DataSpaceNodeConfigurationAgent;
import org.ow2.proactive.scheduler.common.SchedulerConstants;
import org.ow2.proactive.scheduler.common.task.TaskId;
import org.ow2.proactive.scheduler.common.task.dataspaces.InputSelector;
import org.ow2.proactive.scheduler.common.task.dataspaces.OutputSelector;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Throwables.getStackTraceAsString;


public class TaskProActiveDataspaces implements TaskDataspaces {

    private static final transient Logger logger = Logger.getLogger(TaskProActiveDataspaces.class);

    public static final String PA_NODE_DATASPACE_FILE_TRANSFER_THREAD_POOL_SIZE =
            "pa.node.dataspace.filetransfer.threadpoolsize";

    public static final String PA_NODE_DATASPACE_CREATE_FOLDER_HIERARCHY_SEQUENTIALLY =
            "pa.node.dataspace.create_folder_hierarchy_sequentially";

    private transient DataSpacesFileObject SCRATCH;
    private transient DataSpacesFileObject CACHE;
    private transient DataSpacesFileObject INPUT;
    private transient DataSpacesFileObject OUTPUT;
    private transient DataSpacesFileObject GLOBAL;
    private transient DataSpacesFileObject USER;

    private TaskId taskId;
    private transient NamingService namingService;
    private boolean runAsUser;
    private boolean linuxOS;

    private static transient ReentrantLock cacheTransferLock = new ReentrantLock();
    private SpaceInstanceInfo cacheSpaceInstanceInfo;

    private transient StringBuffer clientLogs = new StringBuffer();

    private transient ExecutorService executorTransfer =
            Executors.newFixedThreadPool(getFileTransferThreadPoolSize(),
                    new NamedThreadFactory("FileTransferThreadPool"));

    /**
     * Mainly for testing purposes
     */
    TaskProActiveDataspaces() {

    }

    public TaskProActiveDataspaces(TaskId taskId, NamingService namingService, boolean isRunAsUser) throws Exception {
        this.taskId = taskId;
        this.namingService = namingService;
        this.runAsUser = isRunAsUser;
        this.linuxOS = OperatingSystem.getOperatingSystem() == OperatingSystem.unix;
        initDataSpaces();
    }

    protected int getFileTransferThreadPoolSize() {
        String sizeAsString = System.getProperty(PA_NODE_DATASPACE_FILE_TRANSFER_THREAD_POOL_SIZE);

        int result = Runtime.getRuntime().availableProcessors() * 2;

        if (sizeAsString != null) {
            try {
                result = Integer.parseInt(sizeAsString);
            } catch (NumberFormatException e) {
                // default value will be used
                String message = "Invalid value set for property '"
                        + PA_NODE_DATASPACE_FILE_TRANSFER_THREAD_POOL_SIZE + "': " + sizeAsString;
                logger.warn(message);
                logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
            }
        }

        logger.info("Thread pool size for file transfer is " + result);

        return result;
    }

    private DataSpacesFileObject createTaskIdFolder(DataSpacesFileObject space, String spaceName) {
        if (space != null) {
            String realURI = space.getRealURI();
            // Look for the TASKID pattern at the end of the dataspace URI
            if (realURI.contains(SchedulerConstants.TASKID_DIR_DEFAULT_NAME)) {
                // resolve the taskid subfolder
                DataSpacesFileObject tidOutput;
                try {
                    tidOutput = space.resolveFile(taskId.toString());
                    // create this subfolder
                    tidOutput.createFolder();
                } catch (FileSystemException e) {
                    logger.info("Error when creating the TASKID folder in " + realURI, e);
                    logger.info(spaceName + " space is disabled");
                    return null;
                }
                // assign it to the space
                space = tidOutput;
                logger.info(SchedulerConstants.TASKID_DIR_DEFAULT_NAME + " pattern found, changed " +
                        spaceName + " space to : " + space.getRealURI());
            }
        }
        return space;
    }

    private DataSpacesFileObject resolveToExisting(DataSpacesFileObject space, String spaceName,
            boolean input) {
        if (space == null) {
            logger.info(spaceName + " space is disabled");
            return null;
        }
        // ensure that the remote folder exists (in case we didn't replace any pattern)
        try {
            space = space.ensureExistingOrSwitch(!input);
        } catch (Exception e) {
            logger.info("Error occurred when switching to alternate space root", e);
            logger.info(spaceName + " space is disabled");
            return null;
        }
        if (space == null) {
            logger.info("No existing " + spaceName + " space found");
            logger.info(spaceName + " space is disabled");
        } else {
            logger.debug(spaceName + " space is " + space.getRealURI());
            logger.debug("(other available urls for " + spaceName + " space are " + space.getAllRealURIs() +
                    " )");
        }
        return space;
    }

    private void initDataSpaces() throws Exception {
        // configure node for application
        String appId = taskId.toString();

        // prepare scratch, input, output

        Node node = PAActiveObject.getNode();
        logger.info("Configuring dataspaces for app " + appId + " on " + node.getNodeInformation().getName());
        DataSpacesNodes.configureApplication(node, appId, namingService);

        SCRATCH = PADataSpaces.resolveScratchForAO();
        logger.info("SCRATCH space is " + SCRATCH.getRealURI());

        // Set the scratch folder writable for everyone
        if (!SCRATCH.setWritable(true, false)) {
            logger.warn("Missing permission to change write permissions to " + getScratchFolder());
        }

        InputOutputSpaceConfiguration cacheConfiguration = DataSpaceNodeConfigurationAgent.getCacheSpaceConfiguration();

        if (cacheConfiguration != null) {
            final String cacheName = cacheConfiguration.getName();

            cacheSpaceInstanceInfo = new SpaceInstanceInfo(appId, cacheConfiguration);
            try {
                namingService.register(cacheSpaceInstanceInfo);
            } catch (SpaceAlreadyRegisteredException e) {
                // this is a rare case where the cache space has already been registered for the same task and there was a node failure.
                namingService.unregister(cacheSpaceInstanceInfo.getMountingPoint());
                namingService.register(cacheSpaceInstanceInfo);
            }

            CACHE = initDataSpace(new Callable<DataSpacesFileObject>() {
                @Override
                public DataSpacesFileObject call() throws Exception {
                    return PADataSpaces.resolveOutput(cacheName);
                }
            }, "CACHE", false);
        } else {
            logger.error("No Cache space configuration found, cache space is disabled.");
        }

        INPUT = initDataSpace(new Callable<DataSpacesFileObject>() {
            @Override
            public DataSpacesFileObject call() throws Exception {
                return PADataSpaces.resolveDefaultInput();
            }
        }, "INPUT", true);

        OUTPUT = initDataSpace(new Callable<DataSpacesFileObject>() {
            @Override
            public DataSpacesFileObject call() throws Exception {
                return PADataSpaces.resolveDefaultOutput();
            }
        }, "OUTPUT", false);

        GLOBAL = initDataSpace(new Callable<DataSpacesFileObject>() {
            @Override
            public DataSpacesFileObject call() throws Exception {
                return PADataSpaces.resolveOutput(SchedulerConstants.GLOBALSPACE_NAME);
            }
        }, "GLOBAL", false);

        USER = initDataSpace(new Callable<DataSpacesFileObject>() {
            @Override
            public DataSpacesFileObject call() throws Exception {
                return PADataSpaces.resolveOutput(SchedulerConstants.USERSPACE_NAME);
            }
        }, "USER", false);

    }

    private DataSpacesFileObject initDataSpace(Callable<DataSpacesFileObject> dataSpaceBuilder,
            String dataSpaceName, boolean input) throws Exception {
        try {
            DataSpacesFileObject result = dataSpaceBuilder.call();
            result = resolveToExisting(result, dataSpaceName, input);
            result = createTaskIdFolder(result, dataSpaceName);
            return result;
        } catch (FileSystemException fse) {
            String message = dataSpaceName + " space is disabled";
            logger.warn(message, fse);
            logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
            logDataspacesStatus(getStackTraceAsString(fse), DataspacesStatusLevel.WARNING);
        }
        return null;
    }

    private static String convertDataSpaceURIToFileIfPossible(String dataspaceURI, boolean errorIfNotFile) {
        URI foUri;
        try {
            foUri = new URI(dataspaceURI);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        String answer;
        if (foUri.getScheme() == null || foUri.getScheme().equals("file")) {
            answer = (new File(foUri)).getAbsolutePath();
        } else {
            if (errorIfNotFile) {
                throw new IllegalStateException("Space " + dataspaceURI +
                        " is not accessible via the file system.");
            }
            answer = foUri.toString();
        }
        return answer;
    }


    @Override
    public File getScratchFolder() {
        if (SCRATCH == null) {
            throw new IllegalStateException("SCRATCH space not mounted");
        }

        return new File(convertDataSpaceURIToFileIfPossible(SCRATCH.getRealURI(), true));
    }

    @Override
    public String getScratchURI() {
        if (SCRATCH == null) {
            throw new IllegalStateException("SCRATCH space not mounted");
        }
        return convertDataSpaceURIToFileIfPossible(SCRATCH.getRealURI(), true);
    }

    @Override
    public String getCacheURI() {
        if (CACHE == null) {
            return "";
        }
        return convertDataSpaceURIToFileIfPossible(CACHE.getRealURI(), false);
    }

    @Override
    public String getInputURI() {
        if (INPUT == null) {
            return "";
        }
        return convertDataSpaceURIToFileIfPossible(INPUT.getRealURI(), false);
    }

    @Override
    public String getOutputURI() {
        if (OUTPUT == null) {
            return "";
        }
        return convertDataSpaceURIToFileIfPossible(OUTPUT.getRealURI(), false);
    }

    @Override
    public String getUserURI() {
        if (USER == null) {
            return "";
        }
        return convertDataSpaceURIToFileIfPossible(USER.getRealURI(), false);
    }

    @Override
    public String getGlobalURI() {
        if (GLOBAL == null) {
            return "";
        }
        return convertDataSpaceURIToFileIfPossible(GLOBAL.getRealURI(), false);
    }

    private enum DataspacesStatusLevel {
        ERROR, WARNING, INFO
    }

    private void logDataspacesStatus(String message, DataspacesStatusLevel level) {
        final String eol = System.lineSeparator();
        final boolean hasEol = message.endsWith(eol);

        if (level == DataspacesStatusLevel.ERROR) {
            this.clientLogs.append("[DATASPACES-ERROR] ").append(message).append(hasEol ? "" : eol);
        } else if (level == DataspacesStatusLevel.WARNING) {
            this.clientLogs.append("[DATASPACES-WARNING] ").append(message).append(hasEol ? "" : eol);
        } else if (level == DataspacesStatusLevel.INFO) {
            this.clientLogs.append("[DATASPACES-INFO] ").append(message).append(hasEol ? "" : eol);
        }
    }

    private boolean checkInputSpaceConfigured(DataSpacesFileObject space, String spaceName,
            InputSelector is) {
        if (space == null) {
            String message = "Job " + spaceName +
                    " space is not defined or not properly configured while input files are specified: ";

            logger.error(message);
            logDataspacesStatus(message, DataspacesStatusLevel.ERROR);

            logger.error("--> " + is);
            logDataspacesStatus("--> " + is, DataspacesStatusLevel.ERROR);

            return false;
        }

        return true;
    }

    @Override
    public void copyInputDataToScratch(List<InputSelector> inputSelectors) throws FileSystemException, InterruptedException {
        try {
            if (inputSelectors == null) {
                logger.debug("Input selector is empty, no file to copy");
                return;
            }

            ArrayList<DataSpacesFileObject> inputSpaceFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> outputSpaceFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> globalSpaceFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> userSpaceFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> inputSpaceCacheFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> outputSpaceCacheFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> globalSpaceCacheFiles = new ArrayList<>();
            ArrayList<DataSpacesFileObject> userSpaceCacheFiles = new ArrayList<>();

            FileSystemException exception =
                    findFilesToCopyFromInput(
                            inputSelectors, inputSpaceFiles, outputSpaceFiles,
                            globalSpaceFiles, userSpaceFiles, inputSpaceCacheFiles, outputSpaceCacheFiles, globalSpaceCacheFiles, userSpaceCacheFiles);

            if (exception != null) {
                throw exception;
            }

            String inputSpaceUri = virtualResolve(INPUT);
            String outputSpaceUri = virtualResolve(OUTPUT);
            String globalSpaceUri = virtualResolve(GLOBAL);
            String userSpaceUri = virtualResolve(USER);

            boolean cacheTransferPresent = !inputSpaceCacheFiles.isEmpty() || !outputSpaceCacheFiles.isEmpty() || !globalSpaceCacheFiles.isEmpty() || !userSpaceCacheFiles.isEmpty();
            if (cacheTransferPresent && CACHE != null) {
                cacheTransferLock.lockInterruptibly();
                try {

                    Map<String, DataSpacesFileObject> filesToCopyToCache =
                            createFolderHierarchySequentially(CACHE,
                                    inputSpaceUri, inputSpaceCacheFiles,
                                    outputSpaceUri, outputSpaceCacheFiles,
                                    globalSpaceUri, globalSpaceCacheFiles,
                                    userSpaceUri, userSpaceCacheFiles);

                    List<Future<Boolean>> transferFuturesCache =
                            doCopyInputDataToSpace(CACHE, filesToCopyToCache);

                    handleResults(transferFuturesCache);
                } finally {
                    if (cacheTransferPresent) {
                        cacheTransferLock.unlock();
                    }
                }
            } else if (cacheTransferPresent) {
                logDataspacesStatus("CACHE dataspace is not available while file transfers to cache were required. Check the Node logs for errors.", DataspacesStatusLevel.ERROR);
            }

            Map<String, DataSpacesFileObject> filesToCopyToScratch =
                    createFolderHierarchySequentially(SCRATCH,
                            inputSpaceUri, inputSpaceFiles,
                            outputSpaceUri, outputSpaceFiles,
                            globalSpaceUri, globalSpaceFiles,
                            userSpaceUri, userSpaceFiles);

            List<Future<Boolean>> transferFuturesScratch =
                    doCopyInputDataToSpace(SCRATCH, filesToCopyToScratch);

            handleResults(transferFuturesScratch);

        } finally {
            // display dataspaces error and warns if any
            displayDataspacesStatus();
        }
    }

    private Map<String, DataSpacesFileObject> createFolderHierarchySequentially(DataSpacesFileObject space, String inputSpaceUri,
                                                                                ArrayList<DataSpacesFileObject> inputSpaceFiles,
                                                                                String outputSpaceUri, ArrayList<DataSpacesFileObject> outputSpaceFiles,
                                                                                String globalSpaceUri, ArrayList<DataSpacesFileObject> globalSpaceFiles,
                                                                                String userSpaceUri, ArrayList<DataSpacesFileObject> userSpaceFiles) throws FileSystemException {

        // This map will contain the files that have to be copied.
        Map<String, DataSpacesFileObject> result =
                new HashMap<>(outputSpaceFiles.size() + globalSpaceFiles.size()
                        + userSpaceFiles.size() + inputSpaceFiles.size());

        // Since multiple spaces are involved, it is possible to have
        // a file with the same name present in each space. Consequently,
        // the one to copy has to be selected since there is only a single
        // possible destination, the scratch space.
        // The reverse order of the next calls gives the precedence order
        // of the spaces when the previous situation occurs:
        // output, input, user and global space
        // Precedence is given to the more specific files
        createFolderHierarchySequentially(space, globalSpaceUri, globalSpaceFiles, result);
        createFolderHierarchySequentially(space, userSpaceUri, userSpaceFiles, result);
        createFolderHierarchySequentially(space, inputSpaceUri, inputSpaceFiles, result);
        createFolderHierarchySequentially(space, outputSpaceUri, outputSpaceFiles, result);

        return result;
    }

    /*
     * Create the folder hierarchy and select the files to copy
     * from the specified list of FileObjects.
     */
    protected void createFolderHierarchySequentially(
            DataSpacesFileObject destination, String spaceUri, List<DataSpacesFileObject> spaceFiles,
            Map<String, DataSpacesFileObject> filesToCopy) throws FileSystemException {

        boolean isDebugEnabled = logger.isDebugEnabled();
        boolean isFolderHierarchyCreationEnabled = isCreateFolderHierarchySequentiallyEnabled();
        long startTime = 0;

        if (isDebugEnabled) {
            startTime = System.currentTimeMillis();
        }

        for (DataSpacesFileObject fileObject : spaceFiles) {
            String relativePath = relativize(spaceUri, fileObject);

            if (isFolderHierarchyCreationEnabled) {
                try {
                    DataSpacesFileObject target = destination.resolveFile(relativePath);
                    createFolderHierarchy(isDebugEnabled, fileObject, target);
                } catch (FileSystemException e) {
                    String message = "Could not create folder hierarchy for " +
                            relativePath + " on " + destination.getRealURI();
                    logger.warn(message);
                    logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
                }
            }

            DataSpacesFileObject oldFileObject = filesToCopy.put(relativePath, fileObject);
            if (oldFileObject != null) {
                String message = fileObject.getRealURI()
                        + " will be copied instead of " + oldFileObject.getRealURI() + ".\n "
                        + "Precedence order is output space, input space, user space, global space.";
                logger.warn(message);
                logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
            }
        }

        if (isDebugEnabled) {
            long timeToCreateHierarchySequentially = System.currentTimeMillis() - startTime;
            logger.debug(
                    "Executing TaskProActiveDataspaces#createFolderHierarchySequentially has taken "
                            + timeToCreateHierarchySequentially + " ms");
        }
    }

    protected void createFolderHierarchy(boolean isDebugEnabled, DataSpacesFileObject fileObject,
            DataSpacesFileObject target) throws FileSystemException {

        FileType fileObjectType = fileObject.getType();

        if (FileType.FOLDER.equals(fileObjectType)) {
            if (isDebugEnabled) {
                logger.debug("Creating folder " + target.getRealURI());
            }
            if (!target.exists()) {
                target.createFolder();
                setFolderRightsForRunAsUserMode(target);
            }

        } else if (FileType.FILE.equals(fileObjectType)) {
            DataSpacesFileObject parent = target.getParent();

            if (isDebugEnabled) {
                logger.debug("Creating folder " + parent.getRealURI());
            }
            if (!parent.exists()) {
                parent.createFolder();
                setFolderRightsForRunAsUserMode(parent);
            }
        }
    }

    protected boolean isCreateFolderHierarchySequentiallyEnabled() {
        String property = System.getProperty(PA_NODE_DATASPACE_CREATE_FOLDER_HIERARCHY_SEQUENTIALLY);

        return property == null || "true".equalsIgnoreCase(property);
    }

    /**
     * Sets open file permissions for files copied in RunAsMe mode
     */
    private void setFileRightsForRunAsUserMode(DataSpacesFileObject object) throws FileSystemException {
        if (runAsUser) {
            setRWPermission(object);
        }
    }

    private void setRWPermission(DataSpacesFileObject object) throws FileSystemException {
        object.setReadable(true, false);
        object.setWritable(true, false);
    }

    /**
     * Sets open file permissions for folder copied in RunAsMe mode.
     * The method will set as well recursively the permissions on the parents folder.
     */
    private void setFolderRightsForRunAsUserMode(DataSpacesFileObject object) throws FileSystemException {
        if (runAsUser) {
            setRWPermission(object);
            if (linuxOS) {
                object.setExecutable(true, false);
            }
            DataSpacesFileObject parentObject = null;
            try {
                parentObject = object.getParent();
            } catch (Exception ignored) {
                // in case getParent throws an exception instead of null, we prefer to ignore it and not propagate the permissions further.
            }
            if (parentObject != null) {
                setFolderRightsForRunAsUserMode(parentObject);
            }

        }
    }

    protected void handleResults(List<Future<Boolean>> transferFutures) throws FileSystemException {

        StringBuilder message = new StringBuilder();
        String nl = System.lineSeparator();

        for (Future<Boolean> future : transferFutures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Exception while fetching dataspace transfer result ", e);
                message.append(StackTraceUtil.getStackTrace(e)).append(nl);
            }
        }

        if (message.length() > 0) {
            throw new FileSystemException(
                    "Exception(s) occurred when transferring input file: " + nl + message.toString());
        }
    }

    protected String relativize(String inputSpaceUri, DataSpacesFileObject fileObject) {
        return fileObject.getVirtualURI().replaceFirst(inputSpaceUri + "/?", "");
    }

    private String virtualResolve(DataSpacesFileObject dataSpacesFileObject) {
        if (dataSpacesFileObject == null) {
            return "";
        } else {
            return dataSpacesFileObject.getVirtualURI();
        }
    }

    private FileSystemException findFilesToCopyFromInput(List<InputSelector> inputSelectors,
                                                         ArrayList<DataSpacesFileObject> inResults, ArrayList<DataSpacesFileObject> outResults,
                                                         ArrayList<DataSpacesFileObject> globResults, ArrayList<DataSpacesFileObject> userResults,
                                                         ArrayList<DataSpacesFileObject> inResultsCache, ArrayList<DataSpacesFileObject> outResultsCache,
                                                         ArrayList<DataSpacesFileObject> globResultsCache, ArrayList<DataSpacesFileObject> userResultsCache) {

        FileSystemException toBeThrown = null;

        for (InputSelector is : inputSelectors) {
            org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector selector =
                    new org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector();
            selector.setIncludes(is.getInputFiles().getIncludes());
            selector.setExcludes(is.getInputFiles().getExcludes());

            logger.debug("Selector used is " + selector);

            switch (is.getMode()) {
                case TransferFromInputSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(INPUT, "INPUT", is, selector, inResults);
                    break;
                case TransferFromOutputSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(OUTPUT, "OUTPUT", is, selector, outResults);
                    break;
                case TransferFromGlobalSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(GLOBAL, "GLOBAL", is, selector, globResults);
                    break;
                case TransferFromUserSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(USER, "USER", is, selector, userResults);
                    break;
                case CacheFromInputSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(INPUT, "INPUT", is, selector, inResultsCache);
                    break;
                case CacheFromOutputSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(OUTPUT, "OUTPUT", is, selector, outResultsCache);
                    break;
                case CacheFromGlobalSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(GLOBAL, "GLOBAL", is, selector, globResultsCache);
                    break;
                case CacheFromUserSpace:
                    toBeThrown =
                            findFilesToCopyFromInput(USER, "USER", is, selector, userResultsCache);
                case none:
                    //do nothing
                    break;
            }
        }

        return toBeThrown;
    }

    private List<Future<Boolean>> doCopyInputDataToSpace(
            DataSpacesFileObject space, Map<String, DataSpacesFileObject> filesToCopy) {

        List<Future<Boolean>> transferFutures = new ArrayList<>(filesToCopy.size());

        for (Map.Entry<String, DataSpacesFileObject> entry : filesToCopy.entrySet()) {
            transferFutures.add(parallelFileCopy(entry.getValue(), space, entry.getKey(), true));
        }

        return transferFutures;
    }

    private Future<Boolean> parallelFileCopy(
            final DataSpacesFileObject source,
            final DataSpacesFileObject destinationBase,
            final String destinationRelativeToBase,
            final boolean isInputFile) {

        logger.debug("------------ resolving " + destinationRelativeToBase);

        return executorTransfer.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws FileSystemException {


                DataSpacesFileObject target = destinationBase.resolveFile(destinationRelativeToBase);
                if (!target.exists()) {
                    logger.info("Copying " + source.getRealURI() + " to "
                            + destinationBase.getRealURI() + "/" + destinationRelativeToBase);
                    target.copyFrom(source, FileSelector.SELECT_SELF);
                } else if (source.getContent().getLastModifiedTime() > target.getContent().getLastModifiedTime()) {
                    logger.info("Copying " + source.getRealURI() + " to "
                            + destinationBase.getRealURI() + "/" + destinationRelativeToBase + " (newer version)");
                    target.copyFrom(source, FileSelector.SELECT_SELF);
                } else {
                    logger.info("Destination file " + target.getRealURI() + " is already present and newer.");
                }

                target.refresh();

                if (!target.exists()) {
                    String message =
                            "There was a problem during the copy of " + source.getRealURI() +
                                    " to " + target.getRealURI() +
                                    ". File not present after copy.";
                    logger.error(message);
                    logDataspacesStatus(message, DataspacesStatusLevel.ERROR);
                } else {
                    if (isInputFile) {
                        setFileRightsForRunAsUserMode(target);
                    }
                }
                return true;
            }
        });
    }

    private FileSystemException findFilesToCopyFromInput(
            DataSpacesFileObject space, String spaceName, InputSelector inputSelector,
            org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector selector,
            List<DataSpacesFileObject> results) {

        if (!checkInputSpaceConfigured(space, spaceName, inputSelector)) {
            return null;
        }

        try {
            // A desynchronization has been noticed when multiple dataspaces are mounted on the same folder
            // The call to refresh ensures that the content of the dataspace cache is resynchronized with the disk
            // before the transfer
            space.refresh();

            int oldSize = results.size();

            Utils.findFiles(space, selector, results);

            if (results.size() == oldSize) {
                // we detected that there was no new file in the list
                String message =
                        "No file is transferred from " + spaceName + " space at " +
                                space.getRealURI() + "  for selector " + inputSelector;

                logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
                logger.warn(message);
            }
        } catch (FileSystemException e) {
            logger.warn("Error occurred while transferring files", e);

            String message =
                    "Could not contact " + spaceName + " space at " + space.getRealURI() +
                            ". An error occurred while resolving selector " + inputSelector;

            logDataspacesStatus(message, DataspacesStatusLevel.ERROR);
            logDataspacesStatus(getStackTraceAsString(e), DataspacesStatusLevel.ERROR);

            logger.error(message, e);

            return new FileSystemException(message);
        } catch (NullPointerException e) {
            // nothing to do
            return null;
        }

        return null;
    }

    @Override
    public void copyScratchDataToOutput(List<OutputSelector> outputSelectors) throws FileSystemException {
        try {
            if (outputSelectors == null) {
                logger.debug("Output selector is empty, no file to copy");
                return;
            }

            checkOutputSpacesConfigured(outputSelectors);

            ArrayList<DataSpacesFileObject> results = new ArrayList<>();
            FileSystemException toBeThrown = null;

            for (OutputSelector os : outputSelectors) {
                org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector selector =
                        new org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector();
                selector.setIncludes(os.getOutputFiles().getIncludes());
                selector.setExcludes(os.getOutputFiles().getExcludes());

                switch (os.getMode()) {
                    case TransferToOutputSpace:
                        if (OUTPUT != null) {
                            toBeThrown = copyScratchDataToOutput(OUTPUT, "OUTPUT", os, selector, results);
                        }
                        break;
                    case TransferToGlobalSpace:
                        if (GLOBAL != null) {
                            toBeThrown = copyScratchDataToOutput(GLOBAL, "GLOBAL", os, selector, results);
                        }
                        break;
                    case TransferToUserSpace:
                        if (USER != null) {
                            toBeThrown = copyScratchDataToOutput(USER, "USER", os, selector, results);
                            break;
                        }
                    case none:
                        break;
                }

                results.clear();
            }

            if (toBeThrown != null) {
                throw toBeThrown;
            }

        } finally {
            // display dataspaces error and warns if any
            displayDataspacesStatus();
        }
    }

    private void checkOutputSpacesConfigured(List<OutputSelector> outputSelectors) {
        // Check that output spaces are properly configured, A message is put in the user log output if not
        for (OutputSelector os1 : outputSelectors) {
            switch (os1.getMode()) {
                case TransferToOutputSpace:
                    checkOuputSpaceConfigured(OUTPUT, "OUTPUT", os1);
                    break;
                case TransferToGlobalSpace:
                    checkOuputSpaceConfigured(GLOBAL, "GLOBAL", os1);
                    break;
                case TransferToUserSpace:
                    checkOuputSpaceConfigured(USER, "USER", os1);
                    break;
            }
        }
    }

    private boolean checkOuputSpaceConfigured(DataSpacesFileObject space, String spaceName,
            OutputSelector os) {
        if (space == null) {
            String message =
                    "Job " + spaceName + " space is not defined or not properly configured, " +
                            "while output files are specified :";

            logger.debug(message);

            logDataspacesStatus(message, DataspacesStatusLevel.ERROR);
            logDataspacesStatus("--> " + os, DataspacesStatusLevel.ERROR);

            return false;
        }

        return true;
    }

    @Override
    public void close() {
        if (!executorTransfer.shutdownNow().isEmpty()) {
            String message = "Remaining tasks to execute while closing thread pool used for data transfer";
            logger.error(message);
            logDataspacesStatus(message, DataspacesStatusLevel.ERROR);
        }

        if (CACHE != null) {
            try {
                logger.info("Unregistering cache space : " + cacheSpaceInstanceInfo.getMountingPoint());
                namingService.unregister(cacheSpaceInstanceInfo.getMountingPoint());
            } catch (Exception e) {
                logger.warn("Error occurred when unregistering Cache space", e);
            }
        }

        cleanScratchSpace();
    }

    private void cleanScratchSpace() {
        try {
            File folder = getScratchFolder();
            FileUtils.deleteQuietly(folder);
        } catch (Exception ignored) {
        }
    }

    private FileSystemException copyScratchDataToOutput(DataSpacesFileObject dataspace, String spaceName,
            OutputSelector outputSelector,
            org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector selector,
            List<DataSpacesFileObject> results) {
        try {
            int sizeBeforeHandlingOutput = results.size();

            handleOutput(dataspace, selector, results);

            if (results.size() == sizeBeforeHandlingOutput) {
                String message = "No file is transferred to " + spaceName + " space at " +
                        dataspace.getRealURI() + " for selector " + outputSelector;

                logDataspacesStatus(message, DataspacesStatusLevel.WARNING);
                logger.warn(message);
            }
        } catch (FileSystemException fse) {
            String message = "Error while transferring to " + spaceName + " space at " +
                    dataspace.getRealURI() + " for selector " + outputSelector;

            logDataspacesStatus(message, DataspacesStatusLevel.ERROR);
            logDataspacesStatus(getStackTraceAsString(fse), DataspacesStatusLevel.ERROR);
            logger.error(message, fse);
            return fse;
        }

        return null;
    }

    /**
     * Display the content of the dataspaces status buffer on stderr if non empty.
     */
    private void displayDataspacesStatus() {
        if (this.clientLogs.length() != 0) {
            logger.warn(clientLogs);
            this.clientLogs = new StringBuffer();
        }
    }

    private void handleOutput(final DataSpacesFileObject dataspaceDestination,
            org.objectweb.proactive.extensions.dataspaces.vfs.selector.FileSelector selector,
            List<DataSpacesFileObject> results) throws FileSystemException {

        Utils.findFiles(SCRATCH, selector, results);

        if (logger.isDebugEnabled()) {
            if (results == null || results.size() == 0) {
                logger.debug("No file found to copy from LOCAL space to OUTPUT space");
            } else {
                logger.debug("Files that will be copied from LOCAL space to OUTPUT space :");
            }
        }

        String base = SCRATCH.getVirtualURI();

        Map<String, DataSpacesFileObject> filesToCopy = new HashMap<>(results.size());

        createFolderHierarchySequentially(dataspaceDestination, base, results, filesToCopy);

        ArrayList<Future<Boolean>> transferFutures = new ArrayList<>(results.size());

        for (Map.Entry<String, DataSpacesFileObject> entry : filesToCopy.entrySet()) {
            transferFutures.add(parallelFileCopy(entry.getValue(), dataspaceDestination, entry.getKey(), false));
        }

        handleResults(transferFutures);
    }

}
