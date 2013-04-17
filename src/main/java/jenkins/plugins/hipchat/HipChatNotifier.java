package jenkins.plugins.hipchat;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import static org.apache.commons.lang.StringUtils.chomp;
import static org.apache.commons.lang.StringUtils.split;
import static org.apache.commons.lang.StringUtils.strip;

@SuppressWarnings({ "unchecked" })
public class HipChatNotifier extends Notifier {

   private static final Logger logger = Logger.getLogger(HipChatNotifier.class.getName());

   private String jenkinsUrl;
   private String authToken;
   private String room;
   private Map<String, String> roomsByHost;

   @Override
   public DescriptorImpl getDescriptor() {
      return (DescriptorImpl)super.getDescriptor();
   }

   public String getRoom() {
      return room;
   }

   public String getAuthToken() {
      return authToken;
   }

   public String getJenkinsUrl() {
      return jenkinsUrl;
   }

    public Map<String, String> getRoomsByHost() {
        return roomsByHost;
    }

   public void setJenkinsUrl(String jenkinsUrl) {
      this.jenkinsUrl = jenkinsUrl;
   }

   public void setAuthToken(String authToken) {
      this.authToken = authToken;
   }

   public void setRoom(String room) {
      this.room = room;
   }

    public void setRoomsByHost(Map<String, String> roomsByHost) {
        this.roomsByHost = roomsByHost;
    }

   @DataBoundConstructor
   public HipChatNotifier(String authToken, String room, String jenkinsUrl, Map<String, String> roomsByHost) {
      super();
      this.authToken = authToken;
      this.jenkinsUrl = jenkinsUrl;
      this.room = room;
      this.roomsByHost = roomsByHost;
   }

   public BuildStepMonitor getRequiredMonitorService() {
      return BuildStepMonitor.BUILD;
   }

   public HipChatService newHipChatService(String room) {
      return new StandardHipChatService(getAuthToken(), room == null ? getRoom() : room, "Jenkins");
   }
   
   @Override
   public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
       return true;
   }

   @Extension
   public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
      private String token;
      private String room;
      private String jenkinsUrl;
      private String roomsByHostText;

      public DescriptorImpl() {
         load();
      }

      public String getToken() {
         return token;
      }

      public String getRoom() {
         return room;
      }

      public String getJenkinsUrl() {
         return jenkinsUrl;
      }

       public String getRoomsByHostText() {
           return roomsByHostText;
       }

      public boolean isApplicable(Class<? extends AbstractProject> aClass) {
         return true;
      }

      @Override
      public HipChatNotifier newInstance(StaplerRequest sr) {
         if(token == null) token = sr.getParameter("hipChatToken");
         if(jenkinsUrl == null) jenkinsUrl = sr.getParameter("hipChatJenkinsUrl");
         if(room == null) room = sr.getParameter("hipChatRoom");
         if(roomsByHostText == null) roomsByHostText = sr.getParameter("hipChatRoomsByHostText");
         return new HipChatNotifier(token, room, jenkinsUrl, createRoomsByHostMap(roomsByHostText));
      }

      @Override
      public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
         token = sr.getParameter("hipChatToken");
         room = sr.getParameter("hipChatRoom");
         jenkinsUrl = sr.getParameter("hipChatJenkinsUrl");
         if(jenkinsUrl != null && !jenkinsUrl.endsWith("/")) {
            jenkinsUrl = jenkinsUrl + "/";
         }
         roomsByHostText = sr.getParameter("hipChatRoomsByHostText");
         try {
            new HipChatNotifier(token, room, jenkinsUrl, createRoomsByHostMap(roomsByHostText));
         }
         catch(Exception e) {
            throw new FormException("Failed to initialize notifier - check your global notifier configuration settings", e, "");
         }
         save();
         return super.configure(sr, formData);
      }

       private Map<String, String> createRoomsByHostMap(String roomsByHostText) {
           logger.info("Creating rooms by host mapping for\n" + roomsByHostText + "\n");
           Map<String, String> map = new HashMap<String, String>();
           for (String hostMapping : roomsByHostText.split("\n")) {
               map.put(strip(chomp(split(hostMapping, "=")[0])), strip(chomp(split(hostMapping, "=")[1])));
           }
           return map;
       }
       
      @Override
      public String getDisplayName() {
         return "HipChat Notifications";
      }
   }

    public String resolveVariables(String message, AbstractBuild build, TaskListener listener) {
        if (StringUtils.isBlank(message)) {
            return "";
        }

        try {
            EnvVars env = build.getEnvironment(listener);
            return env != null ? env.expand(message) : message;
        } catch (Exception e) {
            logger.warning("Could not resolve listener for build " + build.getProject().getName());
            return message;
        }
    }

    public String resolveRoom(String parameter) {
        for (String host : roomsByHost.keySet()) {
            if (StringUtils.equals(host, parameter)) {
                logger.info("Room for " + parameter + " resolved to " + roomsByHost.get(host));
                return roomsByHost.get(host);
            }
        }
        logger.info("Room id = " + parameter);
        return parameter;
   }

   public static class HipChatJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {
      private String room;
      private boolean startNotification;
      private String customMessage;

      @DataBoundConstructor
      public HipChatJobProperty(String room, boolean startNotification, String customMessage) {
         this.room = room;
         this.startNotification = startNotification;
         this.customMessage = customMessage;
      }

      @Exported
      public String getRoom() {
         return room;
      }

      @Exported
      public boolean getStartNotification() {
         return startNotification;
      }

       @Exported
       public String getCustomMessage() {
           return customMessage;
       }

      @Override
      public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
         if(startNotification) {
            Map<Descriptor<Publisher>, Publisher> map = build.getProject().getPublishersList().toMap();
            for(Publisher publisher : map.values()) {
               if(publisher instanceof HipChatNotifier) {
                  logger.info("Invoking Started...");
                  new ActiveNotifier((HipChatNotifier)publisher).started(build, listener);
               }
            }
         }
         return super.prebuild(build, listener);
      }

      @Extension
      public static final class DescriptorImpl extends JobPropertyDescriptor {
         public String getDisplayName() {
            return "HipChat Notifications";
         }

         @Override
         public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
         }

         @Override
         public HipChatJobProperty newInstance(StaplerRequest sr, JSONObject formData) throws hudson.model.Descriptor.FormException {
            return new HipChatJobProperty(sr.getParameter("hipChatProjectRoom"), sr.getParameter("hipChatStartNotification") != null, sr.getParameter("hipChatCustomMessage"));
         }
      }
   }
}
