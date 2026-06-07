# CityGuardian AI

<img width="1917" height="1017" alt="SS-Edited" src="https://github.com/user-attachments/assets/dc962d03-3040-405e-a3e6-06349fb85ba2" />


CityGuardian AI is an intelligent, real-time emergency logistics and disaster management simulation platform built in Java. It procedurally generates city grids and simulates devastating natural disasters. An integrated AI optimizer, powered by the NVIDIA NIM API and the Llama 3.1 8B Instruct model, orchestrates automated benchmarking tests to determine the optimal allocation of emergency vehicles required to minimize casualties while strictly adhering to municipal budget constraints.

## Key Features and Concepts

*   **Procedural City Generation**: Dynamically constructs unique city layouts comprising residential zones, commercial districts, road networks, hospitals, and shelters.
*   **Real-time Disaster Simulation**: Simulates the spread and impact of multiple disaster types:
    *   **Fire Outbreaks**: Spreads probabilistically across adjacent buildings over time.
    *   **Earthquakes**: Instantly causes structural damage and critically injures citizens within an epicenter radius.
    *   **Floods**: Progressively inundates low-lying areas, trapping citizens.
*   **Autonomous Agent Optimization**: Conducts isolated visual benchmark tests for each disaster type, iterating through varying quantities of emergency resources. The test data is fed to a Large Language Model to calculate the most cost-effective deployment strategy.
*   **Advanced Pathfinding**: Utilizes the A* search algorithm to navigate emergency vehicles (Firetrucks, Ambulances, Helicopters) through the city grid to rescue citizens and suppress hazards.
*   **Dynamic Resource Allocation**: Includes a fine-grained testing toggle that runs granular vehicle increments (1 to 15) at accelerated simulation speeds for high-precision optimization.

## Technology Stack

*   **Language**: Java (JDK 17+)
*   **GUI Framework**: JavaFX (Canvas rendering, dynamic charts, and UI controls)
*   **Build Tool**: Apache Maven
*   **JSON Serialization**: Google Gson
*   **AI Integration**: NVIDIA NIM API (Model: meta/llama-3.1-8b-instruct)

## Installation Guide

### Prerequisites
*   Java Development Kit (JDK) 17 or higher.
*   Apache Maven installed and configured in your system PATH.
*   An active NVIDIA NIM API key.

### Setup Instructions
1.  Clone the repository:
    ```bash
    git clone https://github.com/PunishingPoison/CityGuardian-AI.git
    cd CityGuardian-AI
    ```
2.  Build the project using Maven:
    ```bash
    mvn clean install
    ```
3.  Run the application:
    ```bash
    mvn javafx:run
    ```

## Walkthrough

### 1. Generating the Environment
Upon launching the application, use the **City Builder** panel on the left to generate a procedural city. This will render a map populated with citizens, buildings, and infrastructure.

### 2. Manual Simulation Control
You can manually adjust the number of Firetrucks, Helicopters, and Ambulances using the top control bar. Click **Start** to run the simulation, and use the **Trigger Disasters** section to spawn a Fire, Earthquake, or Flood. The emergency vehicles will automatically dispatch using A* pathfinding to mitigate the disaster and rescue citizens. 

### 3. AI Optimization
To allow the AI to determine the best resource allocation:
1.  Enter your NVIDIA NIM API key in the right-side **AI Optimization** panel.
2.  (Optional) Check the **Fine-Grained Testing (1 to 15)** box. If checked, the system will run 45 consecutive high-speed benchmark tests. If unchecked, it defaults to a faster 9-test benchmark (increments of 5).
3.  Click **Run AI Optimizer**.
4.  The application will automatically wipe the city, spawn isolated disasters, and test different quantities of vehicles. 
5.  Once testing concludes, the AI will analyze the casualty reports against the calculated municipal budget and output its recommended fleet configuration in the **AI Insights** console.
