package com.game;

import com.game.Race.Race;
import com.game.hardware.StepperAngleConverter;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class TypingRaceApplication {
    public static final int DEFAULT_PORT = 7894;
    public static final double DEFAULT_WHEEL_RADIUS_METERS = 0.03;

    private TypingRaceApplication() { }

    public static void main(String[] args) throws Exception {
        Config config = Config.from(args, System.getenv("WHEEL_RADIUS_METERS"));
        StepperAngleConverter converter = new StepperAngleConverter(config.wheelRadiusMeters);
        Race race = new Race(converter);
        TypingRaceServer server = new TypingRaceServer("0.0.0.0", config.port, race);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();

        System.out.println("Typing Race is ready");
        System.out.println("  This computer: http://localhost:" + config.port);
        for (String address : localAddresses()) {
            System.out.println("  Same Wi-Fi:    http://" + address + ":" + config.port);
        }
        System.out.println("  Distance API:  http://localhost:" + config.port + "/api/race/distances");
        System.out.println("  Wheel radius:  " + config.wheelRadiusMeters + " m (1.8 degree steps)");
    }

    private static Iterable<String> localAddresses() {
        java.util.List<String> addresses = new java.util.ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // localhost remains available even if interface discovery is restricted.
        }
        return addresses;
    }

    static final class Config {
        final int port;
        final double wheelRadiusMeters;

        private Config(int port, double wheelRadiusMeters) {
            this.port = port;
            this.wheelRadiusMeters = wheelRadiusMeters;
        }

        static Config from(String[] args, String radiusEnvironmentValue) {
            int port = DEFAULT_PORT;
            double radius = parsePositiveDouble(radiusEnvironmentValue, DEFAULT_WHEEL_RADIUS_METERS, "WHEEL_RADIUS_METERS");
            for (String arg : args) {
                if (arg.startsWith("--wheel-radius=")) {
                    radius = parsePositiveDouble(arg.substring("--wheel-radius=".length()), radius, "wheel radius");
                } else if (arg.startsWith("--port=")) {
                    port = Integer.parseInt(arg.substring("--port=".length()));
                    if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new Config(port, radius);
        }

        private static double parsePositiveDouble(String value, double fallback, String label) {
            if (value == null || value.trim().isEmpty()) return fallback;
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0) throw new IllegalArgumentException(label + " must be positive");
            return parsed;
        }
    }
}
