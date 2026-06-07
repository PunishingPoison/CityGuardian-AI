package com.cityguardian.engine;

import com.cityguardian.model.City;
import com.cityguardian.model.Citizen;
import com.cityguardian.model.Tile;
import com.cityguardian.model.TileType;

import java.util.Random;

public class CityGenerator {

    public static City generateProceduralCity(int width, int height, long seed) {
        City city = new City(width, height);
        Random random = new Random(seed);
        
        double cx = width / 2.0;
        double cy = height / 2.0;
        double maxDist = Math.sqrt(cx*cx + cy*cy);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Tile t = city.getTile(x, y);
                
                double dist = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));
                double baseElev = 1.0 - (dist / maxDist);
                t.setElevation(baseElev + (random.nextDouble() * 0.2 - 0.1));

                boolean isRoad = (x % 6 == 0) || (y % 6 == 0);

                if (isRoad) {
                    t.setType(TileType.ROAD);
                } else {
                    if (random.nextDouble() < 0.3) {
                        t.setType(TileType.RESIDENTIAL);
                        for (int i=0; i<3; i++) {
                            city.addCitizen(new Citizen("Cit_" + x + "_" + y + "_" + i, 30, x, y));
                        }
                    } else if (random.nextDouble() < 0.1) {
                        t.setType(TileType.COMMERCIAL);
                    } else if (random.nextDouble() < 0.02) {
                        t.setType(TileType.HOSPITAL);
                    } else {
                        t.setType(TileType.EMPTY);
                    }
                }
            }
        }
        return city;
    }
}
