package org.ow2.proactive.scheduler.core.db.schedulerdb;

import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.task.JavaTask;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.executable.JavaExecutable;
import org.ow2.proactive.scheduler.common.task.flow.FlowAction;
import org.ow2.proactive.scheduler.common.task.flow.FlowActionType;
import org.ow2.proactive.scheduler.common.task.flow.FlowScript;
import org.ow2.proactive.scheduler.core.db.RecoveredSchedulerState;
import org.ow2.proactive.scheduler.core.db.SchedulerStateRecoverHelper;
import org.ow2.proactive.scheduler.job.ChangedTasksInfo;
import org.ow2.proactive.scheduler.job.InternalJob;
import org.ow2.proactive.scheduler.task.TaskResultImpl;
import org.ow2.proactive.scheduler.task.internal.InternalTask;
import org.junit.Test;

import java.io.Serializable;


public class TestRestoreWorkflowJobs2 extends BaseSchedulerDBTest {

    @Test
    public void test() throws Exception {
        TaskFlowJob jobDef = createJob();

        InternalJob job = defaultSubmitJobAndLoadInternal(true, jobDef);

        job.start();
        InternalTask mainTask = job.getTask("A");
        startTask(job, mainTask);
        dbManager.jobTaskStarted(job, mainTask, true);

        TaskResultImpl result = new TaskResultImpl(mainTask.getId(), "ok", null, 0);
        FlowAction action = new FlowAction(FlowActionType.IF);
        action.setDupNumber(1);
        action.setTarget("B");
        action.setTargetElse("C");
        ChangedTasksInfo changesInfo = job.terminateTask(false, mainTask.getId(), null, action, result);

        dbManager.updateAfterWorkflowTaskFinished(job, changesInfo, result);

        SchedulerStateRecoverHelper recoverHelper = new SchedulerStateRecoverHelper(dbManager);
        RecoveredSchedulerState state = recoverHelper.recover(-1);
        job = state.getRunningJobs().get(0);
        System.out.println("OK");
    }

    static JavaTask task(String name) {
        JavaTask task = new JavaTask();
        task.setExecutableClassName(TestTask.class.getName());
        task.setName(name);
        return task;
    }

    private TaskFlowJob createJob() throws Exception {
        TaskFlowJob job = new TaskFlowJob();

        JavaTask A = task("A");
        FlowScript ifScript = FlowScript.createIfFlowScript("branch = \"if\";", "B", "C", null);
        A.setFlowScript(ifScript);
        job.addTask(A);

        JavaTask B = task("B");
        job.addTask(B);

        JavaTask C = task("C");
        job.addTask(C);

        return job;
    }

    public static class TestTask extends JavaExecutable {
        @Override
        public Serializable execute(TaskResult... results) throws Throwable {
            String result = "Task " + getReplicationIndex();
            getOut().println(result);
            return result;
        }
    }

}
