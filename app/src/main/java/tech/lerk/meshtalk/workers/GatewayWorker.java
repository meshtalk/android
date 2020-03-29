package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.entities.Preferences;

public abstract class GatewayWorker extends Worker {

    public static final int ERROR_INVALID_SETTINGS = -1;
    public static final int ERROR_NONE = 0;
    public static final int ERROR_URI = 1;
    public static final int ERROR_CONNECTION = 2;
    public static final int ERROR_PARSING = 3;

    protected final SharedPreferences preferences;

    GatewayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    GatewayInfo getGatewayInfo() {
        String defaultGatewayHost = getApplicationContext().getString(R.string.pref_default_message_gateway_host);
        String defaultGatewayPort = getApplicationContext().getString(R.string.pref_default_message_gateway_port);
        String defaultGatewayProtocol = getApplicationContext().getString(R.string.pref_default_message_gateway_protocol);
        String defaultGatewayPath = getApplicationContext().getString(R.string.pref_default_message_gateway_path);
        String gatewayHost = preferences.getString(Preferences.MESSAGE_GATEWAY_HOST.toString(), defaultGatewayHost);
        int gatewayPort = Integer.parseInt(preferences.getString(Preferences.MESSAGE_GATEWAY_PORT.toString(), defaultGatewayPort));
        String gatewayPath = preferences.getString(Preferences.MESSAGE_GATEWAY_PATH.toString(), defaultGatewayPath);
        String gatewayProtocol = preferences.getString(Preferences.MESSAGE_GATEWAY_PROTOCOL.toString(), defaultGatewayProtocol);
        return new GatewayInfo(gatewayProtocol, gatewayHost, gatewayPort, gatewayPath);
    }

    public static class GatewayInfo {
        private final String protocol;
        private final String host;
        private final int port;
        private final String path;

        private GatewayInfo(String protocol, String host, int port, String path) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.path = path;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getPath() {
            return path;
        }

        @NonNull
        @Override
        public String toString() {
            String h = protocol + "://" + this.host + ":" + port;
            String p = !this.path.isEmpty() ? "/" + this.path : "";
            return h + p;
        }
    }
}
