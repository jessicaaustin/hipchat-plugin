package jenkins.plugins.hipchat;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public interface FineGrainedNotifier {

   @SuppressWarnings("rawtypes")
   void started(AbstractBuild r, TaskListener listener);

   @SuppressWarnings("rawtypes")
   void deleted(AbstractBuild r);

   @SuppressWarnings("rawtypes")
   void finalized(AbstractBuild r);

   @SuppressWarnings("rawtypes")
   void completed(AbstractBuild r, TaskListener listener);

}
