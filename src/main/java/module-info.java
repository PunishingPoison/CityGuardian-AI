module com.cityguardian {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires java.net.http;
    requires com.google.gson;

    opens com.cityguardian to javafx.fxml;
    opens com.cityguardian.controller to javafx.fxml;
    
    exports com.cityguardian;
    exports com.cityguardian.model;
    exports com.cityguardian.model.building;
    exports com.cityguardian.model.disaster;
    exports com.cityguardian.model.resource;
    exports com.cityguardian.controller;
    exports com.cityguardian.engine;
}
