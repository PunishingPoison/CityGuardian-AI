package com.cityguardian.model.disaster;

import com.cityguardian.model.City;
import com.cityguardian.model.Tile;
import java.util.ArrayList;
import java.util.List;

public class FireDisaster extends Disaster {
    private double timeAccumulator = 0;
    private final double spreadInterval = 1.0; // Tuned for 3 vs 15 firetruck balance

    public FireDisaster(int startX, int startY, Severity severity) {
        super(startX, startY, severity);
        this.spreadSpeed = 1.0;
        this.damageRadius = 1;
    }

    @Override
    public void evolve(City city, double deltaTime) {
        if (!active) return;
        
        timeAccumulator += deltaTime;
        if (timeAccumulator >= spreadInterval) {
            timeAccumulator = 0;
            spread(city);
        }
    }
    
    private void spread(City city) {
        List<Tile> newlyInfected = new ArrayList<>();
        
        for (int x = 0; x < city.getWidth(); x++) {
            for (int y = 0; y < city.getHeight(); y++) {
                Tile t = city.getTile(x, y);
                if (t != null && t.hasDisaster()) {
                    if (Math.random() < 0.05) { 
                        t.setHasDisaster(false);
                        t.setRiskLevel(0.0);
                        if (t.getType() != com.cityguardian.model.TileType.ROAD) {
                            t.setType(com.cityguardian.model.TileType.BURNT);
                        }
                        continue;
                    }

                    // Spread to neighbors randomly
                    int[] dx = {-1, 1, 0, 0};
                    int[] dy = {0, 0, -1, 1};
                    for (int i = 0; i < 4; i++) {
                        Tile neighbor = city.getTile(x + dx[i], y + dy[i]);
                        if (neighbor != null && !neighbor.hasDisaster() && 
                            neighbor.getType() != com.cityguardian.model.TileType.BURNT && 
                            neighbor.getType() != com.cityguardian.model.TileType.WATER) {
                            if (Math.random() < 0.3) { // 30% chance to spread per interval
                                newlyInfected.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
        
        for (Tile t : newlyInfected) {
            t.setHasDisaster(true);
            t.setRiskLevel(1.0);
        }
    }
}
