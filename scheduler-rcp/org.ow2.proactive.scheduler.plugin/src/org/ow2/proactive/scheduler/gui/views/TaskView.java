/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
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
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 */
package org.ow2.proactive.scheduler.gui.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.task.TaskInfo;
import org.ow2.proactive.scheduler.common.task.TaskId;
import org.ow2.proactive.scheduler.common.task.TaskState;
import org.ow2.proactive.scheduler.gui.composite.TaskComposite;
import org.ow2.proactive.scheduler.gui.data.JobsController;
import org.ow2.proactive.scheduler.gui.data.TableManager;


/***
 * This view display many informations about tasks contains in a job.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
public class TaskView extends ViewPart {

    /** the view part id */
    public static final String ID = "org.ow2.proactive.scheduler.gui.views.TaskView";

    // the shared instance
    private static TaskView instance = null;
    private static boolean isDisposed = true;
    private TaskComposite taskComposite = null;

    /**
     * This is the default constructor
     */
    public TaskView() {
        instance = this;
    }

    /**
     * This method clear the view
     */
    public void clear() {
        taskComposite.clear();
    }

    /**
     * To update fully the view with the new informations about the given job
     *
     * @param job a job
     */
    public void fullUpdate(JobState job) {
        if (!taskComposite.isDisposed()) {
            final JobState aJob = job;
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    taskComposite.setTasks(aJob.getId(), aJob.getTasks());
                }
            });
        }
    }

    /**
     * To update fully the view with the new informations about given jobs
     *
     * @param jobs a list of job
     */
    public void fullUpdate(List<JobState> jobs) {
        if (!taskComposite.isDisposed()) {
            final int numberOfJobs = jobs.size();
            final ArrayList<TaskState> tasks = new ArrayList<TaskState>();
            for (JobState job : jobs)
                tasks.addAll(job.getTasks());

            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    taskComposite.setTasks(numberOfJobs, tasks);
                }
            });
        }
    }

    /**
     * To update only one line of the jobs informations displayed in the view. use this method to
     * avoid flicker
     *
     * @param task the task to update
     */
    public void lineUpdate(final TaskState task) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                taskComposite.changeLine(task);
            }
        });
    }

    /**
     * To display or not the view
     *
     * @param isVisible
     */
    public void setVisible(boolean isVisible) {
        if (taskComposite != null) {
            taskComposite.setVisible(isVisible);
        }
    }

    /**
     * To enabled or not the view
     *
     * @param isEnabled
     */
    public void setEnabled(boolean isEnabled) {
        if (taskComposite != null) {
            taskComposite.setEnabled(isEnabled);
        }
    }

    public TaskId getIdOfSelectedTask() {
        return taskComposite.getIdOfSelectedTask();
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static TaskView getInstance() {
        if (isDisposed) {
            return null;
        }
        return instance;
    }

    // -------------------------------------------------------------------- //
    // ------------------------ extends ViewPart -------------------------- //
    // -------------------------------------------------------------------- //
    /**
     * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public void createPartControl(Composite parent) {
        isDisposed = false;
        taskComposite = new TaskComposite(parent);

        TableManager tableManager = TableManager.getInstance();
        if (tableManager != null) {
            List<JobId> jobIds = tableManager.getJobsIdOfSelectedItems();
            if (jobIds.size() == 1) {
                JobsController jobsController = JobsController.getLocalView();
                if (jobsController != null) {
                    fullUpdate(jobsController.getJobById(jobIds.get(0)));
                }
            } else if (jobIds.size() > 0) {
                JobsController jobsController = JobsController.getLocalView();
                if (jobsController != null) {
                    fullUpdate(jobsController.getJobsByIds(jobIds));
                }
            }
        }
    }

    /**
     * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
     */
    @Override
    public void setFocus() {
        // TableManager tableManager = TableManager.getInstance();
        // if (tableManager != null) {
        // TableItem item = tableManager.getLastSelectedItem();
        // if (item != null)
        // fullUpdate(JobsController.getInstance().getJobById((IntWrapper)
        // item.getData()));
        // }
    }

    /**
     * @see org.eclipse.ui.part.WorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        isDisposed = true;
        taskComposite.dispose();
        super.dispose();
    }
}