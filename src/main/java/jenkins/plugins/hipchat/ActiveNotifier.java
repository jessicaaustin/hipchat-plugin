package jenkins.plugins.hipchat;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.CauseAction;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

@SuppressWarnings("rawtypes")
public class ActiveNotifier implements FineGrainedNotifier {

   private static final Logger logger = Logger.getLogger(HipChatListener.class.getName());

   HipChatNotifier notifier;

   public ActiveNotifier(HipChatNotifier notifier) {
      super();
      this.notifier = notifier;
   }

   private HipChatService getHipChat(AbstractBuild r, TaskListener listener) {
      AbstractProject<?, ?> project = r.getProject();
      String projectRoom = notifier.resolveRoom(notifier.resolveVariables(project.getProperty(HipChatNotifier.HipChatJobProperty.class).getRoom(), r, listener));
      return notifier.newHipChatService(projectRoom);
   }

   public void deleted(AbstractBuild r) {}

   public void started(AbstractBuild build, TaskListener listener) {
      String changes = getChanges(build, listener);
      CauseAction cause = build.getAction(CauseAction.class);
      if(changes != null) {
         notifyStart(build, listener, changes);
      }
      else if(cause != null) {
         MessageBuilder message = new MessageBuilder(notifier, build);
         message.appendCustomMessage(listener);
         message.append(" - ");
         message.append(cause.getShortDescription());
         notifyStart(build, listener, message.toString());
      }
      else {
         notifyStart(build, listener, getBuildStatusMessage(build, listener));
      }
   }

   private void notifyStart(AbstractBuild build, TaskListener listener, String message) {
      getHipChat(build, listener).publish(message, "green");
   }

    public void finalized(AbstractBuild r) {}

   public void completed(AbstractBuild r, TaskListener listener) {
      getHipChat(r, listener).publish(getBuildStatusMessage(r, listener), getBuildColor(r));
   }

   String getChanges(AbstractBuild r, TaskListener listener) {
      if(!r.hasChangeSetComputed()) {
         logger.info("No change set computed...");
         return null;
      }
      ChangeLogSet changeSet = r.getChangeSet();
      List<Entry> entries = new LinkedList<Entry>();
      Set<AffectedFile> files = new HashSet<AffectedFile>();
      for(Object o : changeSet.getItems()) {
         Entry entry = (Entry)o;
         logger.info("Entry " + o);
         entries.add(entry);
         files.addAll(entry.getAffectedFiles());
      }
      if(entries.isEmpty()) {
         logger.info("Empty change...");
         return null;
      }
      Set<String> authors = new HashSet<String>();
      for(Entry entry : entries) {
         authors.add(entry.getAuthor().getDisplayName());
      }
      MessageBuilder message = new MessageBuilder(notifier, r);
      message.appendCustomMessage(listener);
      message.append(" - ");
      message.append("Started by changes from ");
      message.append(StringUtils.join(authors, ", "));
      message.append(" (");
      message.append(files.size());
      message.append(" file(s) changed)");
      return message.toString();
   }

   static String getBuildColor(AbstractBuild r) {
      Result result = r.getResult();
      if(result == Result.SUCCESS) {
         return "green";
      }
      else if(result == Result.FAILURE) {
         return "red";
      }
      else {
         return "yellow";
      }
   }

   String getBuildStatusMessage(AbstractBuild r, TaskListener listener) {
      MessageBuilder message = new MessageBuilder(notifier, r);
      message.appendCustomMessage(listener);
      message.append(" - ");
      message.appendStatusMessage();
      message.appendDuration();
      return message.toString();
   }

   public static class MessageBuilder {
      private StringBuffer message;
      private HipChatNotifier notifier;
      private AbstractBuild build;

      public MessageBuilder(HipChatNotifier notifier, AbstractBuild build) {
         this.notifier = notifier;
         this.message = new StringBuffer();
         this.build = build;
         startMessage();
      }

      public MessageBuilder appendStatusMessage() {
         message.append(getStatusMessage(build));
         return this;
      }

      static String getStatusMessage(AbstractBuild r) {
         if(r.isBuilding()) {
            return "Starting...";
         }
         Result result = r.getResult();
         if(result == Result.SUCCESS) return "Success";
         if(result == Result.FAILURE) return "<b>FAILURE</b>";
         if(result == Result.ABORTED) return "ABORTED";
         if(result == Result.NOT_BUILT) return "Not built";
         if(result == Result.UNSTABLE) return "Unstable";
         return "Unknown";
      }

      public MessageBuilder append(String string) {
         message.append(string);
         return this;
      }

      public MessageBuilder append(Object string) {
         message.append(string.toString());
         return this;
      }

      private MessageBuilder startMessage() {
         String url = notifier.getJenkinsUrl() + build.getUrl();
         message.append("<a href='").append(url).append("'>");
         message.append(build.getProject().getDisplayName());
         message.append(" ");
         message.append(build.getDisplayName());
         message.append("</a>");
         message.append(" ");
         return this;
      }

       public MessageBuilder appendCustomMessage(TaskListener listener) {
           HipChatNotifier.HipChatJobProperty hipChatJobConfig = (HipChatNotifier.HipChatJobProperty) build.getProject().getProperty(HipChatNotifier.HipChatJobProperty.class);
           String customMessage = hipChatJobConfig.getCustomMessage();
           if (StringUtils.isBlank(customMessage)) {
               return this;
           }

           // Expand variables if applicable
           customMessage = notifier.resolveVariables(customMessage, build, listener);

           message.append(" (").append(customMessage).append(")");
           return this;
       }


      public MessageBuilder appendDuration() {
         message.append(" after ");
         message.append(build.getDurationString());
         return this;
      }

      public String toString() {
         return message.toString();
      }
   }
}
