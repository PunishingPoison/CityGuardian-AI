package com.cityguardian.engine;

import com.cityguardian.model.City;
import com.cityguardian.model.Citizen;
import com.cityguardian.model.Tile;
import com.cityguardian.model.TileType;
import javafx.animation.AnimationTimer;

public class SimulationEngine {
    private City city;
    private boolean running = false;
    private double speedMultiplier = 1.0;
    
    private AnimationTimer timer;
    private long lastUpdate = 0;
    
    private int maxFiretrucks = 3;
    private int maxHelicopters = 3;
    private int maxAmbulances = 3;
    
    // Callbacks to UI
    private Runnable onTick;

    public SimulationEngine(City city, Runnable onTick) {
        this.city = city;
        this.onTick = onTick;
        
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }
                
                // Elapsed time in seconds
                double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;
                
                if (running) {
                    tick(deltaTime * speedMultiplier);
                }
            }
        };
    }
    
    public void start() {
        running = true;
        timer.start();
        lastUpdate = 0;
    }
    
    public void pause() {
        running = false;
    }
    
    public void setSpeed(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }
    
    public void setMaxFiretrucks(int max) { this.maxFiretrucks = max; }
    public void setMaxHelicopters(int max) { this.maxHelicopters = max; }
    public void setMaxAmbulances(int max) { this.maxAmbulances = max; }
    
    private void tick(double deltaTime) {
        // 1. Update disasters
        for (com.cityguardian.model.disaster.Disaster d : city.getDisasters()) {
            d.evolve(city, deltaTime);
        }
        
        // 2. Update citizens (move along path)
        for (Citizen c : city.getCitizens()) {
            if (c.isDead() || c.isEvacuated()) continue;
            
            int cx = (int) Math.round(c.getX());
            int cy = (int) Math.round(c.getY());
            Tile currentTile = city.getTile(cx, cy);
            
            if (currentTile != null && currentTile.hasDisaster()) {
                c.takeDamage(10.0 * deltaTime);
            }
            
            boolean cityHasDisaster = !city.getDisasters().isEmpty();
            
            if (c.isInjured()) {
                c.setEvacuationPath(null); // Injured citizens cannot walk, they must wait for ambulances
                if (!isCitizenLoaded(c)) {
                    c.takeDamage(1.5 * deltaTime); // Slowly bleed out if not loaded in an ambulance
                }
            }
            
            if (c.getEvacuationPath() != null && c.getCurrentPathIndex() < c.getEvacuationPath().size()) {
                Tile nextStep = c.getEvacuationPath().get(c.getCurrentPathIndex());
                double dx = nextStep.getX() - c.getX();
                double dy = nextStep.getY() - c.getY();
                double dist = Math.sqrt(dx*dx + dy*dy);
                
                if (dist < 0.05) {
                    c.advancePath();
                } else {
                    double moveX = (dx / dist) * c.getMovementSpeed() * deltaTime;
                    double moveY = (dy / dist) * c.getMovementSpeed() * deltaTime;
                    double moveDist = Math.sqrt(moveX*moveX + moveY*moveY);
                    
                    if (moveDist >= dist) {
                        c.setPosition(nextStep.getX(), nextStep.getY());
                        c.advancePath();
                    } else {
                        c.setPosition(c.getX() + moveX, c.getY() + moveY);
                    }
                }
                
                if (c.getCurrentPathIndex() >= c.getEvacuationPath().size()) {
                    Tile end = c.getEvacuationPath().get(c.getEvacuationPath().size() - 1);
                    if (end.getType() == TileType.SHELTER || end.getType() == TileType.HOSPITAL) {
                        c.setEvacuated(true);
                    }
                    c.setEvacuationPath(null);
                }
            }
        }
        
        // 3. Update resources
        boolean hasFire = false;
        boolean hasQuake = false;
        boolean hasFlood = false;
        for (com.cityguardian.model.disaster.Disaster d : city.getDisasters()) {
            if (d instanceof com.cityguardian.model.disaster.FloodDisaster) hasFlood = true;
            else if (d instanceof com.cityguardian.model.disaster.FireDisaster) hasFire = true;
            else if (d instanceof com.cityguardian.model.disaster.EarthquakeDisaster) hasQuake = true;
        }

        long truckCount = city.getResources().stream().filter(r -> r instanceof com.cityguardian.model.resource.FireTruck).count();
        long heliCount = city.getResources().stream().filter(r -> r instanceof com.cityguardian.model.resource.Helicopter).count();
        long ambCount = city.getResources().stream().filter(r -> r instanceof com.cityguardian.model.resource.Ambulance).count();
        
        if (hasFire && truckCount < maxFiretrucks) {
            Tile road = findRandomRoadTile();
            city.addResource(new com.cityguardian.model.resource.FireTruck("FT-" + truckCount, road.getX(), road.getY()));
        }
        if (hasQuake && ambCount < maxAmbulances) {
            Tile road = findRandomRoadTile();
            city.addResource(new com.cityguardian.model.resource.Ambulance("AMB-" + ambCount, road.getX(), road.getY(), 10)); // Capacity 10
        }
        if (hasFlood && heliCount < maxHelicopters) {
            Tile road = findRandomRoadTile();
            city.addResource(new com.cityguardian.model.resource.Helicopter("H-" + heliCount, road.getX(), road.getY()));
        }

        for (com.cityguardian.model.resource.EmergencyResource r : city.getResources()) {
            if (r.getStatus() == com.cityguardian.model.resource.EmergencyResource.Status.AVAILABLE) {
                Citizen target = null;
                double minDist = Double.MAX_VALUE;
                
                for (Citizen c : city.getCitizens()) {
                    if (c.isDead() || c.isEvacuated() || isCitizenLoaded(c) || isTileTargeted((int)c.getX(), (int)c.getY())) continue;
                    
                    boolean validTarget = false;
                    if ((r instanceof com.cityguardian.model.resource.FireTruck || r instanceof com.cityguardian.model.resource.Ambulance) && c.isInjured()) {
                        validTarget = true;
                    } else if (r instanceof com.cityguardian.model.resource.Helicopter) {
                        Tile t = city.getTile((int)c.getX(), (int)c.getY());
                        if (t != null && t.getType() == com.cityguardian.model.TileType.WATER) {
                            validTarget = true;
                        }
                    }
                    
                    if (validTarget) {
                        double dist = Math.sqrt(Math.pow(c.getX() - r.getX(), 2) + Math.pow(c.getY() - r.getY(), 2));
                        if (dist < minDist) {
                            minDist = dist;
                            target = c;
                        }
                    }
                }
                
                Tile targetTile = null;
                if (target == null && r instanceof com.cityguardian.model.resource.FireTruck) {
                    double minFireDist = Double.MAX_VALUE;
                    for (int x = 0; x < city.getWidth(); x++) {
                        for (int y = 0; y < city.getHeight(); y++) {
                            Tile t = city.getTile(x, y);
                            if (t != null && t.hasDisaster() && t.getType() != com.cityguardian.model.TileType.WATER) {
                                if (isFireTileTargeted(x, y)) continue;
                                double dist = Math.sqrt(Math.pow(x - r.getX(), 2) + Math.pow(y - r.getY(), 2));
                                if (dist < minFireDist) {
                                    minFireDist = dist;
                                    targetTile = t;
                                }
                            }
                        }
                    }
                }
                
                if (target != null || targetTile != null) {
                    if (r instanceof com.cityguardian.model.resource.FireTruck) {
                        com.cityguardian.model.resource.FireTruck ft = (com.cityguardian.model.resource.FireTruck)r;
                        Tile tTarget;
                        if (target != null) {
                            ft.setTargetCitizen(target);
                            tTarget = findNearestRoadTo(city, (int)target.getX(), (int)target.getY());
                        } else {
                            ft.setTargetTile(targetTile);
                            tTarget = findNearestRoadTo(city, targetTile.getX(), targetTile.getY());
                        }
                        Tile tStart = city.getTile((int)r.getX(), (int)r.getY());
                        if (tStart != null && tTarget != null) {
                            ft.setPath(EvacuationPlanner.findPathAStar(city, tStart, tTarget, true));
                        }
                    } else if (r instanceof com.cityguardian.model.resource.Ambulance) {
                        com.cityguardian.model.resource.Ambulance amb = (com.cityguardian.model.resource.Ambulance)r;
                        amb.setTargetCitizen(target);
                        Tile tTarget = findNearestRoadTo(city, (int)target.getX(), (int)target.getY());
                        Tile tStart = city.getTile((int)r.getX(), (int)r.getY());
                        if (tStart != null && tTarget != null) {
                            amb.setPath(EvacuationPlanner.findPathAStar(city, tStart, tTarget, true));
                        }
                    } else if (r instanceof com.cityguardian.model.resource.Helicopter) {
                        ((com.cityguardian.model.resource.Helicopter)r).setTargetCitizen(target);
                    }
                    r.setStatus(com.cityguardian.model.resource.EmergencyResource.Status.DISPATCHED);
                }
            } else if (r.getStatus() == com.cityguardian.model.resource.EmergencyResource.Status.DISPATCHED) {
                if (r instanceof com.cityguardian.model.resource.Helicopter) {
                    com.cityguardian.model.resource.Helicopter h = (com.cityguardian.model.resource.Helicopter)r;
                    Citizen t = h.getTargetCitizen();
                    if (t == null || t.isEvacuated() || t.isDead()) {
                        h.setStatus(com.cityguardian.model.resource.EmergencyResource.Status.AVAILABLE);
                        continue;
                    }
                    double dx = t.getX() - h.getCurrentX();
                    double dy = t.getY() - h.getCurrentY();
                    double dist = Math.sqrt(dx*dx + dy*dy);
                    if (dist < 0.5) {
                        performAreaRescue((int)h.getCurrentX(), (int)h.getCurrentY(), 1, false);
                        h.performAction();
                    } else {
                        double moveX = (dx / dist) * h.getSpeed() * deltaTime;
                        double moveY = (dy / dist) * h.getSpeed() * deltaTime;
                        h.setCurrentPos(h.getCurrentX() + moveX, h.getCurrentY() + moveY);
                        h.setPosition((int)h.getCurrentX(), (int)h.getCurrentY());
                    }
                } else if (r instanceof com.cityguardian.model.resource.FireTruck) {
                    com.cityguardian.model.resource.FireTruck ft = (com.cityguardian.model.resource.FireTruck)r;
                    Citizen tCit = ft.getTargetCitizen();
                    Tile tTile = ft.getTargetTile();
                    
                    boolean targetLost = false;
                    if (tCit != null && (tCit.isEvacuated() || tCit.isDead())) targetLost = true;
                    if (tTile != null && !tTile.hasDisaster()) targetLost = true;
                    if (tCit == null && tTile == null) targetLost = true;
                    if (ft.getPath() == null || ft.getPath().isEmpty()) targetLost = true;
                    
                    if (targetLost) {
                        ft.setStatus(com.cityguardian.model.resource.EmergencyResource.Status.AVAILABLE);
                        ft.setTargetCitizen(null);
                        ft.setTargetTile(null);
                        continue;
                    }
                    
                    if (ft.getPathIndex() < ft.getPath().size()) {
                        Tile nextStep = ft.getPath().get(ft.getPathIndex());
                        double dx = nextStep.getX() - ft.getCurrentX();
                        double dy = nextStep.getY() - ft.getCurrentY();
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.1) {
                            ft.advancePath();
                        } else {
                            double moveX = (dx / dist) * ft.getSpeed() * deltaTime;
                            double moveY = (dy / dist) * ft.getSpeed() * deltaTime;
                            double moveDist = Math.sqrt(moveX*moveX + moveY*moveY);
                            if (moveDist >= dist) {
                                ft.setCurrentPos(nextStep.getX(), nextStep.getY());
                                ft.advancePath();
                            } else {
                                ft.setCurrentPos(ft.getCurrentX() + moveX, ft.getCurrentY() + moveY);
                            }
                            ft.setPosition((int)ft.getCurrentX(), (int)ft.getCurrentY());
                        }
                    } else {
                        // Reached the end of the path (the road near the target)
                        int targetX = tCit != null ? (int)tCit.getX() : (int)tTile.getX();
                        int targetY = tCit != null ? (int)tCit.getY() : (int)tTile.getY();
                        performAreaRescue(targetX, targetY, 2, true); // Firefighters use hose to clear a 5x5 block (radius 2)
                        ft.performAction();
                    }
                } else if (r instanceof com.cityguardian.model.resource.Ambulance) {
                    com.cityguardian.model.resource.Ambulance amb = (com.cityguardian.model.resource.Ambulance)r;
                    Citizen tCit = amb.getTargetCitizen();
                    
                    if (tCit == null || tCit.isEvacuated() || tCit.isDead() || amb.getPath() == null || amb.getPath().isEmpty()) {
                        amb.setStatus(com.cityguardian.model.resource.EmergencyResource.Status.AVAILABLE);
                        amb.setTargetCitizen(null);
                        continue;
                    }
                    
                    if (amb.getPathIndex() < amb.getPath().size()) {
                        Tile nextStep = amb.getPath().get(amb.getPathIndex());
                        double dx = nextStep.getX() - amb.getCurrentX();
                        double dy = nextStep.getY() - amb.getCurrentY();
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.1) {
                            amb.advancePath();
                        } else {
                            double moveX = (dx / dist) * amb.getSpeed() * deltaTime;
                            double moveY = (dy / dist) * amb.getSpeed() * deltaTime;
                            double moveDist = Math.sqrt(moveX*moveX + moveY*moveY);
                            if (moveDist >= dist) {
                                amb.setCurrentPos(nextStep.getX(), nextStep.getY());
                                amb.advancePath();
                            } else {
                                amb.setCurrentPos(amb.getCurrentX() + moveX, amb.getCurrentY() + moveY);
                            }
                            amb.setPosition((int)amb.getCurrentX(), (int)amb.getCurrentY());
                        }
                    } else {
                        int targetX = tCit != null ? (int)tCit.getX() : (int)amb.getCurrentX();
                        int targetY = tCit != null ? (int)tCit.getY() : (int)amb.getCurrentY();
                        
                        for (int x = targetX - 2; x <= targetX + 2; x++) {
                            for (int y = targetY - 2; y <= targetY + 2; y++) {
                                for (Citizen c : city.getCitizens()) {
                                    if (!c.isDead() && !c.isEvacuated() && c.isInjured() && !isCitizenLoaded(c) && (int)c.getX() == x && (int)c.getY() == y) {
                                        if (!amb.isFull()) {
                                            amb.loadCitizen(c);
                                            c.setPosition(-1000, -1000); // Hide them off-map while in transport
                                        }
                                    }
                                }
                            }
                        }
                        
                        amb.performAction(); // Switches to RETURNING if full, or AVAILABLE if not
                    }
                }
            } else if (r.getStatus() == com.cityguardian.model.resource.EmergencyResource.Status.RETURNING) {
                if (r instanceof com.cityguardian.model.resource.Ambulance) {
                    com.cityguardian.model.resource.Ambulance amb = (com.cityguardian.model.resource.Ambulance)r;
                    
                    if (amb.getPath() == null || amb.getPath().isEmpty()) {
                        Tile tStart = city.getTile((int)amb.getCurrentX(), (int)amb.getCurrentY());
                        Tile nearestHospital = findNearestHospital(city, (int)amb.getCurrentX(), (int)amb.getCurrentY());
                        if (tStart != null && nearestHospital != null) {
                            amb.setPath(EvacuationPlanner.findPathAStar(city, tStart, nearestHospital, true));
                        }
                    }
                    
                    if (amb.getPath() != null && amb.getPathIndex() < amb.getPath().size()) {
                        Tile nextStep = amb.getPath().get(amb.getPathIndex());
                        double dx = nextStep.getX() - amb.getCurrentX();
                        double dy = nextStep.getY() - amb.getCurrentY();
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.1) {
                            amb.advancePath();
                        } else {
                            double moveX = (dx / dist) * amb.getSpeed() * deltaTime;
                            double moveY = (dy / dist) * amb.getSpeed() * deltaTime;
                            double moveDist = Math.sqrt(moveX*moveX + moveY*moveY);
                            if (moveDist >= dist) {
                                amb.setCurrentPos(nextStep.getX(), nextStep.getY());
                                amb.advancePath();
                            } else {
                                amb.setCurrentPos(amb.getCurrentX() + moveX, amb.getCurrentY() + moveY);
                            }
                            amb.setPosition((int)amb.getCurrentX(), (int)amb.getCurrentY());
                        }
                    } else {
                        // Reached hospital! Unload passengers.
                        amb.performAction();
                    }
                }
            }
        }
        
        // Notify UI
        if (onTick != null) {
            onTick.run();
        }
    }
    
    private Tile findNearestSafeZone(int startX, int startY) {
        Tile nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (int x = 0; x < city.getWidth(); x++) {
            for (int y = 0; y < city.getHeight(); y++) {
                Tile t = city.getTile(x, y);
                if (t != null && (t.getType() == TileType.SHELTER || t.getType() == TileType.HOSPITAL) && !t.hasDisaster()) {
                    double dist = Math.sqrt(Math.pow(x - startX, 2) + Math.pow(y - startY, 2));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = t;
                    }
                }
            }
        }
        return nearest != null ? nearest : city.getTile(0, 0);
    }
    
    private Tile findRandomRoadTile() {
        for (int i = 0; i < 100; i++) {
            int rx = (int)(Math.random() * city.getWidth());
            int ry = (int)(Math.random() * city.getHeight());
            Tile t = city.getTile(rx, ry);
            if (t != null && t.getType() == TileType.ROAD) {
                return t;
            }
        }
        return city.getTile(5, 5); // Fallback
    }
    
    private void performAreaRescue(int cx, int cy, int radius, boolean isFiretruck) {
        // Rescue citizens within bounding box
        for (Citizen c : city.getCitizens()) {
            if (c.isDead() || c.isEvacuated()) continue;
            if (Math.abs(c.getX() - cx) <= radius && Math.abs(c.getY() - cy) <= radius) {
                c.setEvacuated(true);
            }
        }
        
        // Firetrucks extinguish fires in the radius using their hose
        if (isFiretruck) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int y = cy - radius; y <= cy + radius; y++) {
                    Tile t = city.getTile(x, y);
                    if (t != null && t.hasDisaster() && t.getType() != com.cityguardian.model.TileType.WATER) {
                        if (Math.abs(x - cx) <= radius && Math.abs(y - cy) <= radius) {
                            t.setHasDisaster(false);
                            t.setRiskLevel(0.0);
                            if (t.getType() == com.cityguardian.model.TileType.RESIDENTIAL || 
                                t.getType() == com.cityguardian.model.TileType.COMMERCIAL ||
                                t.getType() == com.cityguardian.model.TileType.HOSPITAL ||
                                t.getType() == com.cityguardian.model.TileType.SHELTER) {
                                t.setType(com.cityguardian.model.TileType.BURNT);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private boolean isTileTargeted(int x, int y) {
        for (com.cityguardian.model.resource.EmergencyResource r : city.getResources()) {
            Citizen target = null;
            if (r instanceof com.cityguardian.model.resource.Helicopter) {
                target = ((com.cityguardian.model.resource.Helicopter)r).getTargetCitizen();
            } else if (r instanceof com.cityguardian.model.resource.FireTruck) {
                target = ((com.cityguardian.model.resource.FireTruck)r).getTargetCitizen();
            } else if (r instanceof com.cityguardian.model.resource.Ambulance) {
                target = ((com.cityguardian.model.resource.Ambulance)r).getTargetCitizen();
            }
            if (target != null && (int)target.getX() == x && (int)target.getY() == y) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isFireTileTargeted(int x, int y) {
        for (com.cityguardian.model.resource.EmergencyResource r : city.getResources()) {
            if (r instanceof com.cityguardian.model.resource.FireTruck) {
                Tile t = ((com.cityguardian.model.resource.FireTruck)r).getTargetTile();
                if (t != null && t.getX() == x && t.getY() == y) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private Tile findNearestRoadTo(City city, int startX, int startY) {
        Tile nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int x = 0; x < city.getWidth(); x++) {
            for (int y = 0; y < city.getHeight(); y++) {
                Tile t = city.getTile(x, y);
                if (t != null && t.getType() == TileType.ROAD) {
                    double dist = Math.sqrt(Math.pow(x - startX, 2) + Math.pow(y - startY, 2));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = t;
                    }
                }
            }
        }
        return nearest != null ? nearest : city.getTile(startX, startY);
    }
    
    private boolean isCitizenLoaded(Citizen c) {
        for (com.cityguardian.model.resource.EmergencyResource r : city.getResources()) {
            if (r instanceof com.cityguardian.model.resource.Ambulance) {
                if (((com.cityguardian.model.resource.Ambulance)r).getLoadedCitizens().contains(c)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private Tile findNearestHospital(City city, int x, int y) {
        Tile nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < city.getWidth(); i++) {
            for (int j = 0; j < city.getHeight(); j++) {
                Tile t = city.getTile(i, j);
                if (t != null && t.getType() == com.cityguardian.model.TileType.HOSPITAL) {
                    double dist = Math.sqrt(Math.pow(x - i, 2) + Math.pow(y - j, 2));
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = t;
                    }
                }
            }
        }
        return nearest != null ? nearest : findNearestRoadTo(city, x, y);
    }
}
