package com.thepiratebrowser.android;

final class LatestRequestGate {
    private long generation;
    private boolean destroyed;

    synchronized Ticket begin(String query) {
        return new Ticket(++generation, query);
    }

    synchronized boolean accept(Ticket ticket) {
        return !destroyed && ticket.generation == generation;
    }

    synchronized boolean isAlive() {
        return !destroyed;
    }

    synchronized void destroy() {
        destroyed = true;
        generation++;
    }

    static final class Ticket {
        final long generation;
        final String query;

        private Ticket(long generation, String query) {
            this.generation = generation;
            this.query = query;
        }
    }
}
