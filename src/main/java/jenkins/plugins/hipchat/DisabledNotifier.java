package jenkins.plugins.hipchat;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

@SuppressWarnings("rawtypes")
public class DisabledNotifier implements FineGrainedNotifier {
   public void started(AbstractBuild r, TaskListener listener) {}

   public void deleted(AbstractBuild r) {}

   public void finalized(AbstractBuild r) {}

   public void completed(AbstractBuild r, TaskListener listener) {}
}
