package com.cityguardian.engine;

import com.cityguardian.model.City;
import com.cityguardian.model.Citizen;
import com.cityguardian.model.disaster.Disaster.Severity;
import com.cityguardian.model.disaster.EarthquakeDisaster;
import com.cityguardian.model.disaster.FireDisaster;

public class HeadlessSimulator {

    /**
     * Runs a headless simulation for a given number of seconds and returns the number of casualties.
     */
    public static int runTest(long seed, int width, int height, int firetrucks, int ambulances, int helicopters) {
        // 1. Generate the determinisitic city
        City testCity = CityGenerator.generateProceduralCity(width, height, seed);
        
        // 2. Setup the engine without UI callbacks
        SimulationEngine engine = new SimulationEngine(testCity, null);
        engine.setMaxFiretrucks(firetrucks);
        engine.setMaxAmbulances(ambulances);
        engine.setMaxHelicopters(helicopters);
        
        // 3. Trigger massive disaster (Fire + Quake) to stress test
        int startX = width / 2;
        int startY = height / 2;
        
        // Fire outbreak
        for (int x = startX - 6; x <= startX + 6; x++) {
            for (int y = startY - 6; y <= startY + 6; y++) {
                com.cityguardian.model.Tile t = testCity.getTile(x, y);
                if (t != null && t.getType() != com.cityguardian.model.TileType.WATER && t.getType() != com.cityguardian.model.TileType.ROAD) {
                    t.setHasDisaster(true);
                    t.setRiskLevel(1.0);
                }
            }
        }
        testCity.addDisaster(new FireDisaster(startX, startY, Severity.HIGH));
        
        // Earthquake
        testCity.addDisaster(new EarthquakeDisaster(startX, startY, Severity.CRITICAL));
        
        // 4. Run ticks synchronously for 60 seconds (assuming 0.1s per tick)
        double deltaTime = 0.1;
        int iterations = 600; 
        
        // Expose tick publicly using reflection or just make tick public.
        // Wait, tick() is private in SimulationEngine. I need to make it public or package-private.
        // I will just use reflection for speed.
        try {
            java.lang.reflect.Method tickMethod = SimulationEngine.class.getDeclaredMethod("tick", double.class);
            tickMethod.setAccessible(true);
            for (int i = 0; i < iterations; i++) {
                tickMethod.invoke(engine, deltaTime);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 5. Count casualties
        int casualties = 0;
        for (Citizen c : testCity.getCitizens()) {
            if (c.isDead()) {
                casualties++;
            }
        }
        return casualties;
    }
}
