package com.itmarts.rutadecamion;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPolygon;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.RoadElement;
import com.here.android.mpa.guidance.NavigationManager;
import com.here.android.mpa.mapping.AndroidXMapFragment;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.DrivingDirection;
import com.here.android.mpa.routing.DynamicPenalty;
import com.here.android.mpa.routing.Route;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;
import com.here.android.mpa.routing.RoutingZone;
import com.itmarts.rutadecamion.databinding.ActivityMainBinding;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    private Map map = null;

    private AndroidXMapFragment mapFragment = null;

    private AppCompatActivity m_activity;
    private NavigationManager m_navigationManager;

    // UTILIDADES PARA LA RUTA
    private MapRoute m_mapRoute;
    private Route m_route;
    private GeoBoundingBox m_geoBoundingBox;

    private boolean m_addAvoidedAreas;
    private boolean m_isExcludeRoutingZones;
    private boolean m_foregroundServiceStarted;

    private GeoCoordinate puntoFinal;
    private MapMarker markerStart;
    private MapMarker markerEnd;

    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final String[] RUNTIME_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
//        inicialize();
//        clickEvents();

        if (hasPermissions(this, RUNTIME_PERMISSIONS)) {
            inicialize();
            clickEvents();
        } else {
            ActivityCompat
                    .requestPermissions(this, RUNTIME_PERMISSIONS, REQUEST_CODE_ASK_PERMISSIONS);
        }

    }

    private void generateCoords() {
        try {
            Intent intent = getIntent();

            List<String> coords;
            List<String> list = new ArrayList<>();
            String dat = "";
            if (intent != null && intent.getData() != null) {
                if (intent.getScheme().equals("geo")) {
                    Uri data = intent.getData();
                    dat = data.toString();
                    Pattern p = Pattern.compile("(|-)[0-9]+\\.[0-9]+");
                    Matcher m = p.matcher(dat);
                    while (m.find()) {
                        list.add(m.group());
                    }
                    coords = new ArrayList<>(list);
                    if (coords.size() > 0) {
                        puntoFinal = new GeoCoordinate(Double.parseDouble(coords.get(0)), Double.parseDouble(coords.get(1)));
                        binding.TIECordLong.setText(String.valueOf(puntoFinal.getLongitude()));
                        binding.TIECordLat.setText(String.valueOf(puntoFinal.getLatitude()));
                    }
                }
            }
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Ha ocurrido un error", Snackbar.LENGTH_LONG).show();
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void inicialize() {
        mapFragment = (AndroidXMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.init(error -> {
            if (error == OnEngineInitListener.Error.NONE) {
                map = mapFragment.getMap();
                map.setCenter(new GeoCoordinate(21.153033, -101.678526), Map.Animation.NONE);
//                map.setZoomLevel((map.getMaxZoomLevel() + map.getMinZoomLevel()) / 5);
                map.setZoomLevel(13.2);
                m_navigationManager = NavigationManager.getInstance();

                generateCoords();
            } else {
                Log.e("ERROR_MAP", "ERROR: Cannot initialize Map Fragment");
            }
        });
    }

    private void clickEvents() {
        binding.btnGenerarRuta.setOnClickListener(v -> {
            binding.btnStartNavegacion.setVisibility(View.GONE);
            map.removeMapObject(m_mapRoute);
            map.removeMapObject(markerStart);
            map.removeMapObject(markerEnd);
            m_mapRoute = null;
            if (puntoFinal != null) {
                createRoute(Collections.emptyList(), puntoFinal);
            } else {
                Snackbar.make(binding.getRoot(), "Es necesario establecer el destino desde TourSolver Mobile", Snackbar.LENGTH_LONG).show();
            }
        });

        binding.btnStartNavegacion.setOnClickListener(v -> startNavigation());
    }

    private void createRoute(final List<RoutingZone> excludedRountingZones, GeoCoordinate destination) {

        if (m_route != null) {
            m_navigationManager.stop();
            map.zoomTo(m_geoBoundingBox, Map.Animation.NONE, 0f);
            m_route = null;
        }

        // Initialize a CoreRouter
        CoreRouter coreRouter = new CoreRouter();

        // Initialize a RoutePlan
        RoutePlan routePlan = new RoutePlan();

        // Initialize a RouteOption
        RouteOptions routeOptions = new RouteOptions();

        // Set a transport mode
        routeOptions.setTransportMode(RouteOptions.TransportMode.TRUCK);

        float heightCamion = 4f, largoCamion = 18f;

        routeOptions.setTruckTunnelCategory(RouteOptions.TunnelCategory.UNDEFINED)
                .setTruckLength(largoCamion)
                .setTruckHeight(heightCamion)
                .setTruckWidth(3f)
                .setTruckLimitedWeight(19f)
                .setTruckType(RouteOptions.TruckType.TRUCK)
                .setTruckDifficultTurnsAllowed(false)
                .setTruckTrailersCount(1);

        // Disable highway in this route
        routeOptions.setHighwaysAllowed(true);

        // Calculate the shortest route available
        routeOptions.setRouteType(RouteOptions.Type.FASTEST);

        // Calculate 1 route
        routeOptions.setRouteCount(1);

        // Exclude routing zones
        if (!excludedRountingZones.isEmpty()) {
            routeOptions.excludeRoutingZones(toStringIds(excludedRountingZones));
        }

        if (m_addAvoidedAreas) {
            DynamicPenalty dynamicPenalty = new DynamicPenalty();
            // There are two option to avoid certain areas during routing
            // 1. Add banned area using addBannedArea API
            GeoPolygon geoPolygon = new GeoPolygon();
            geoPolygon.add(Arrays.asList(new GeoCoordinate(52.631692, 13.437591),
                    new GeoCoordinate(52.631905, 13.437787),
                    new GeoCoordinate(52.632577, 13.438357)));
            // Note, the maximum supported number of banned areas is 20.
            dynamicPenalty.addBannedArea(geoPolygon);

            // 1. Add banned road link using addRoadPenalty API
            // Note, map data needs to be present to get RoadElement by the GeoCoordinate.
            RoadElement roadElement = RoadElement
                    .getRoadElement(new GeoCoordinate(52.406611, 13.194916), "MAC");
            if (roadElement != null) {
                dynamicPenalty.addRoadPenalty(roadElement, DrivingDirection.DIR_BOTH,
                        0/*new speed*/);
            }
            coreRouter.setDynamicPenalty(dynamicPenalty);

        }

        routePlan.setRouteOptions(routeOptions);

        //Define waypoints for the route
        //START:
        RouteWaypoint startPoint = new RouteWaypoint(new GeoCoordinate(21.1444989, -101.6940116));
        //END:
        GeoCoordinate endCord = new GeoCoordinate(21.142914, -101.680730);

        //MARKERS
        RouteWaypoint endPoint = new RouteWaypoint(destination == null ? endCord : destination);
        markerStart = new MapMarker(new GeoCoordinate(21.1444989, -101.6940116));
        map.addMapObject(markerStart);
        markerEnd = new MapMarker(destination == null ? endCord : destination);
        map.addMapObject(markerEnd);

        // Add both waypoints to the route plan
        routePlan.addWaypoint(startPoint);
        routePlan.addWaypoint(endPoint);

        coreRouter.calculateRoute(routePlan, new Router.Listener<List<RouteResult>, RoutingError>() {
            @Override
            public void onProgress(int i) {

            }

            @Override
            public void onCalculateRouteFinished(@NonNull List<RouteResult> routeResults, @NonNull RoutingError routingError) {
                if (routingError == RoutingError.NONE) {
                    m_route = routeResults.get(0).getRoute();

                    MapRoute mapRoute = new MapRoute(routeResults.get(0).getRoute());

                    //Show the maneuver number on top of the route
                    mapRoute.setManeuverNumberVisible(true);

                    map.addMapObject(mapRoute);

                    m_geoBoundingBox = routeResults.get(0).getRoute().getBoundingBox();
                    map.zoomTo(m_geoBoundingBox, Map.Animation.NONE, Map.MOVE_PRESERVE_ORIENTATION);

//                    startNavigation();
                    binding.btnStartNavegacion.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error:route calculation returned error code: " + routingError,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    static List<String> toStringIds(List<RoutingZone> excludedRoutingZones) {
        ArrayList<String> ids = new ArrayList<>();
        for (RoutingZone zone : excludedRoutingZones) {
            ids.add(zone.getId());
        }
        return ids;
    }

    private void startForegroundService() {
        if (!m_foregroundServiceStarted) {
            m_foregroundServiceStarted = true;
            Intent startIntent = new Intent(this, ForegroundService.class);
            startIntent.setAction(ForegroundService.START_ACTION);
            getApplicationContext().startService(startIntent);
        }
    }

    private void stopForegroundService() {
        if (m_foregroundServiceStarted) {
            m_foregroundServiceStarted = false;
            Intent stopIntent = new Intent(this, ForegroundService.class);
            stopIntent.setAction(ForegroundService.STOP_ACTION);
            getApplicationContext().startService(stopIntent);
        }
    }

    private void startNavigation() {
        m_navigationManager.setMap(map);
        mapFragment.getPositionIndicator().setVisible(true);

//        m_navigationManager.startNavigation(m_route);
        m_navigationManager.simulate(m_route, 60);
        map.setTilt(60);
        startForegroundService();

        m_navigationManager.setMapUpdateMode(NavigationManager.MapUpdateMode.ROADVIEW);

        addNavigationListeners();
    }

    private void addNavigationListeners() {
        m_navigationManager.addNavigationManagerEventListener(
                new WeakReference<NavigationManager.NavigationManagerEventListener>(m_navigationManagerEventListener)
        );

        m_navigationManager.addPositionListener(
                new WeakReference<NavigationManager.PositionListener>(m_positionListener)
        );
    }

    private NavigationManager.PositionListener m_positionListener = new NavigationManager.PositionListener() {
        @Override
        public void onPositionUpdated(@NonNull GeoPosition geoPosition) {

        }
    };

    private NavigationManager.NavigationManagerEventListener m_navigationManagerEventListener = new NavigationManager.NavigationManagerEventListener() {
        @Override
        public void onRunningStateChanged() {
            Toast.makeText(getApplicationContext(), "Running state changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onNavigationModeChanged() {
            Toast.makeText(getApplicationContext(), "Navigation mode changed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopoverReached(int i) {
//            Toast.makeText(getApplicationContext(), "UwU", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDestinationReached() {
//            Toast.makeText(getApplicationContext(), "T.T", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onEnded(NavigationManager.NavigationMode navigationMode) {
            Toast.makeText(getApplicationContext(), navigationMode + " was ended", Toast.LENGTH_SHORT).show();
            stopForegroundService();
        }

        @Override
        public void onMapUpdateModeChanged(NavigationManager.MapUpdateMode mapUpdateMode) {
            Toast.makeText(getApplicationContext(), "Map update mode is changed to " + mapUpdateMode,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRouteUpdated(@NonNull Route route) {
            Toast.makeText(getApplicationContext(), "Route updated", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCountryInfo(@NonNull String s, @NonNull String s1) {
            Toast.makeText(getApplicationContext(), "Country info updated from " + s + " to " + s1,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onMapDataInsufficient() {
//            Toast.makeText(getApplicationContext(), "n.n", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onMapDataAvailable() {
//            Toast.makeText(getApplicationContext(), "XD", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (m_navigationManager != null) {
            stopForegroundService();
            m_navigationManager.stop();
        }
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS: {
                for (int index = 0; index < permissions.length; index++) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {

                        /*
                         * If the user turned down the permission request in the past and chose the
                         * Don't ask again option in the permission request system dialog.
                         */
                        if (!ActivityCompat
                                .shouldShowRequestPermissionRationale(this, permissions[index])) {
                            Toast.makeText(this, "Required permission " + permissions[index]
                                            + " not granted. "
                                            + "Please go to settings and turn on for sample app",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Required permission " + permissions[index]
                                    + " not granted", Toast.LENGTH_LONG).show();
                        }
                    }
                }

                inicialize();
                clickEvents();
                break;
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}