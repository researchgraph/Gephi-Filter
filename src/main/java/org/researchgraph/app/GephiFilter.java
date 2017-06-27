package org.researchgraph.app;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder;
import org.gephi.filters.plugin.graph.GiantComponentBuilder;
import org.gephi.filters.plugin.graph.InDegreeRangeBuilder;
import org.gephi.filters.plugin.graph.NeighborsBuilder;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.noverlap.NoverlapLayout;
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.rotate.RotateLayout;
import org.gephi.layout.plugin.scale.ScaleLayout;
import org.gephi.layout.plugin.random.RandomLayout;
import org.gephi.layout.plugin.openord.OpenOrdLayout;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

public class GephiFilter {

    public static void main(String[] args){
        // expected arguments - input graph file and filters config file
        if (args.length == 0) {
            System.out.println("Error: Insufficient arguments. Required - [configFile].");
            System.exit(1);
        }

        File configFile=new File(args[0]);
        if(!configFile.exists()) {
            System.out.println("Error: Graph filters configuration file does not exist.");
            System.exit(1);
        }

        GephiFilter gephiFilter = new GephiFilter();
        try {
            gephiFilter.process(gephiFilter, args[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //////// BEGIN Helpers

    public void process(GephiFilter gFilter, String configFileName) throws IOException {
        //Initialization - create ProjectController
        ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
        projectController.newProject();
        Workspace workspace = projectController.getCurrentWorkspace();

        //Get models and controllers for this new workspace - will be useful later
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();

        // Read configurations from file
        FileReader configReader = new FileReader(configFileName);
        BufferedReader br = new BufferedReader(configReader);

        String inputLine;

        // Import input graph
        while ((inputLine = br.readLine()) != null)
            if (inputLine.length()>0 && inputLine.charAt(0)!='#') break;

        Container container;
        try {
            File file = new File(new URI("file:"+inputLine));    //Define path to the graph file
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED);   //Force DIRECTED
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        // Get output format
        while ((inputLine = br.readLine()) != null)
            if (inputLine.length()>0 && inputLine.charAt(0)!='#') break;

        String exportFormat = inputLine;

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);

        // Print graph stats if well imported
        DirectedGraph graph = graphModel.getDirectedGraph();
        System.out.println("SUCCESS: " + inputLine + " is imported with " + graph.getNodeCount() + " nodes and " + graph.getEdgeCount() + " edges.");

        // Process filters
        Query rootQuery = null;
        Query parentQuery = null;
        while ((inputLine = br.readLine()) != null) {
            if (inputLine.length() == 0) continue;
            switch (inputLine.charAt(0)) {
                case '#' :
                    // skip comments
                    continue;

                case '-' :
                    String methodName = inputLine.split("\\s+")[1];
                    String methodCategory = methodName.substring(methodName.length()-6, methodName.length());
                    inputLine = br.readLine();
                    String[] args = null;
                    if (inputLine != null) args = inputLine.split("\\s+");
                    if (args.length==1 && args[0].equals("")) args = new String[0];

                    if (methodCategory.equals("Filter")) {
                        try {
                            Query q = (Query) executor(gFilter, graph, methodName, args);
                            if (rootQuery == null) {
                                rootQuery = q;
                                parentQuery = q;
                            } else {
                                filterController.setSubQuery(parentQuery, q);
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }

                    if (methodCategory.equals("Layout")) {
                        try {
                            executor(gFilter, graph, methodName, args);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                default :

            }
        }
        br.close();
        configReader.close();

        // Apply chained filters
        GraphView view = filterController.filter(rootQuery);
        // Set the filter result as the visible view
        graphModel.setVisibleView(view);

        // Print graph stats after filtering
        graph = graphModel.getDirectedGraphVisible();
        System.out.println("SUCCESS: After filtering the graph is with " + graph.getNodeCount() + " nodes and " + graph.getEdgeCount() + " edges.");

        // Export to specified format
        gFilter.exporter(exportFormat);
        System.out.println("SUCCESS: The processed graph is exported to output." + exportFormat);
    }

    public Object executor(GephiFilter gFilter, DirectedGraph graph, String methodName, String[] args) throws ClassNotFoundException {
        Method method;
        try {
            method = getClass().getDeclaredMethod(methodName, DirectedGraph.class, String[].class);
        } catch (NoSuchMethodException e) {
            System.out.println("Skip: [" + methodName + "] is not implemented");
            return null;
        }

        Object result = null;
        try {
            Object[] params = {graph, args};
            result = method.invoke(gFilter, params);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            System.out.println("Skip: [" + methodName + "] due to internal error");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            System.out.println("Skip: [" + methodName + "] due to internal error");
        }
        return result;
    }

    public void exporter(String exportFormat) {
        //Export only visible graph
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        GraphExporter exporter = (GraphExporter) ec.getExporter(exportFormat);
        exporter.setExportVisible(true);  //Only exports the visible (filtered) graph
        ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
        Workspace workspace = projectController.getCurrentWorkspace();
        exporter.setWorkspace(workspace);
        try {
            ec.exportFile(new File("output." + exportFormat), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    //////// END Helpers


    //////// BEGIN Filters

    public Query GiantComponentsFilter(DirectedGraph graph, String[] args) {
        System.out.println("-- Apply Giant Components Filter");
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);

        GiantComponentBuilder.GiantComponentFilter giantComponentFilter = new GiantComponentBuilder.GiantComponentFilter();
        giantComponentFilter.init(graph);
        Query queryGiantComponent = filterController.createQuery(giantComponentFilter);
        return queryGiantComponent;
    }

    public Query DegreeFilter(DirectedGraph graph, String[] args) {
        System.out.println("-- Apply Degree Filter");
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        int lowerBound = Integer.parseInt(args[0]);
        int upperBound = Integer.parseInt(args[1]);

        DegreeRangeBuilder.DegreeRangeFilter degreeFilter = new DegreeRangeBuilder.DegreeRangeFilter();
        degreeFilter.init(graph);
        degreeFilter.setRange(new Range(lowerBound, upperBound));
        Query queryDegreeFilter = filterController.createQuery(degreeFilter);
        return queryDegreeFilter;
    }

    public Query InDegreeFilter(DirectedGraph graph, String[] args) {
        System.out.println("-- Apply In-Degree Filter");
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        int lowerBound = Integer.parseInt(args[0]);
        int upperBound = Integer.parseInt(args[1]);

        InDegreeRangeBuilder.InDegreeRangeFilter inDegreeRangeFilter = new InDegreeRangeBuilder.InDegreeRangeFilter();
        inDegreeRangeFilter.init(graph);
        inDegreeRangeFilter.setRange(new Range(lowerBound, upperBound));
        Query queryIndegree = filterController.createQuery(inDegreeRangeFilter);
        return  queryIndegree;
    }

    public Query NeighborNetworkFilter(DirectedGraph graph, String[] args) {
        System.out.println("-- Apply Neighbor Network Filter");
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        int depth = Integer.parseInt(args[0]);

        NeighborsBuilder.NeighborsFilter neighborsFilter = new NeighborsBuilder.NeighborsFilter();
        neighborsFilter.setDepth(depth);
        neighborsFilter.setSelf(true);
        Query queryNeighbor = filterController.createQuery(neighborsFilter);
        return queryNeighbor;
    }

    //////// END Filters


    //////// BEGIN Layouts

    public void RotateLayout (DirectedGraph graph, String[] args) {
        double angle = 0.0;
        if (args.length > 0) angle = Double.parseDouble(args[0]);
        System.out.println("-- Run Rotate Layout for " + angle + " degree");
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        RotateLayout layout = new RotateLayout(null, angle);
        layout.setGraphModel(graphModel);

        layout.initAlgo();
        layout.goAlgo();
        layout.endAlgo();
    }

    public void ScaleLayout (DirectedGraph graph, String[] args) {
        double factor = 1.0;
        if (args.length > 0) factor = Double.parseDouble(args[0]);
        System.out.println("-- Run Scale Layout with factor " + factor);
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        ScaleLayout layout = new ScaleLayout(null, factor);
        layout.setGraphModel(graphModel);

        layout.initAlgo();
        layout.goAlgo();
        layout.endAlgo();
    }

    //todo: to be fixed
//    public void NoverlapLayout (DirectedGraph graph, String[] args) {
//        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
//
//        NoverlapLayout layout = new NoverlapLayout(null);
//        layout.setGraphModel(graphModel);
//        layout.resetPropertiesValues();
//        layout.initAlgo();
//
//        if (layout.canAlgo()) {
//            System.out.println("-- Run Noverlap Layout");
//            layout.goAlgo();
//        }
//
//        layout.endAlgo();
//    }

    public void ForceAtlasLayout (DirectedGraph graph, String[] args) {
        int passes = 1;
        if (args.length > 0) passes = Integer.parseInt(args[0]);
        System.out.println("-- Run ForceAtlas Layout for " + passes + " passes");
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        ForceAtlasLayout layout = new ForceAtlasLayout(null);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.initAlgo();

        for (int i = 0; i < passes && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }

    public void ForceAtlas2Layout (DirectedGraph graph, String[] args) {
        int passes = 1;
        if (args.length > 0) passes = Integer.parseInt(args[0]);
        System.out.println("-- Run ForceAtlas2 Layout for " + passes + " passes");
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        ForceAtlas2 layout = new ForceAtlas2(null);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.initAlgo();

        for (int i = 0; i < passes && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }

    public void OpenOrdLayout (DirectedGraph graph, String[] args) {
        int passes = 1;
        if (args.length > 0) passes = Integer.parseInt(args[0]);
        System.out.println("-- Run OpenOrd Layout for " + passes + " passes");
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        OpenOrdLayout layout = new OpenOrdLayout(null);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setNumIterations(passes);

        layout.initAlgo();
        layout.goAlgo();
        layout.endAlgo();
    }

    public void YifanHuLayout (DirectedGraph graph, String[] args) {
        int passes = 1;
        if (args.length > 0) passes = Integer.parseInt(args[0]);
        System.out.println("-- Run YifanHu Layout for "+passes+" passes");
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        layout.setOptimalDistance(200f);
        layout.initAlgo();

        for (int i = 0; i < passes && layout.canAlgo(); i++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }

    public void RandomLayout (DirectedGraph graph, String[] args) {
        double spaceSize = 50.0;
        if (args.length > 0) spaceSize = Double.parseDouble(args[0]);
        System.out.println("-- Run Random Layout with space size " + spaceSize);
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        RandomLayout layout = new RandomLayout(null, spaceSize);
        layout.setGraphModel(graphModel);

        layout.initAlgo();
        layout.goAlgo();
        layout.endAlgo();
    }

    //////// END Layouts
}