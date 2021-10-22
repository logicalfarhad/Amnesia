package controller;


import algorithms.Algorithm;
import algorithms.clusterbased.ClusterBasedAlgorithm;
import algorithms.flash.Flash;
import algorithms.flash.LatticeNode;
import algorithms.kmanonymity.Apriori;
import algorithms.mixedkmanonymity.MixedApriori;
import algorithms.parallelflash.ParallelFlash;
import anonymizationrules.AnonymizationRules;
import anonymizeddataset.AnonymizedDataset;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;
import data.*;
import dataverse.DataverseConnection;
import dataverse.DataverseFile;
import dictionary.DictionaryString;
import exceptions.DateParseException;
import exceptions.LimitException;
import exceptions.NotFoundValueException;
import graph.DatasetsExistence;
import graph.Graph;
import graph.Node;
import hierarchy.HierToJson;
import hierarchy.Hierarchy;
import hierarchy.distinct.*;
import hierarchy.ranges.*;
import jsoninterface.View;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import solutions.*;
import solutions.SolutionStatistics.SolutionAnonValues;
import statistics.ColumnsNamesAndTypes;
import statistics.HierarchiesAndLevels;
import statistics.Queries;
import statistics.Results;
import zenodo.ZenodoConnection;
import zenodo.ZenodoFile;
import zenodo.ZenodoFilesToJson;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * @author jimakos
 */

/*session variables


 */

@SpringBootApplication
public class AppCon {

    private static final Class<AppCon> applicationClass = AppCon.class;
    public static String os = "linux";

    public static void main(String[] args) {
        SpringApplication.run(applicationClass, args);
    }
}

@RestController
class AppController {
    private static final String os = AppCon.os;

    String rootPath = System.getProperty("catalina.home");

    @PostMapping("/action/restart")
    public static @ResponseBody
    void restart(HttpSession session) {
        try {
            String inputPath = (String) session.getAttribute("inputpath");
            int index = inputPath.lastIndexOf(File.separator);

            if (os.equals("online")) {
                File dir = new File(inputPath.substring(0, index));
                File dir2 = new File(inputPath);

                FileUtils.cleanDirectory(dir2);
                for (File file : dir.listFiles()) {
                    if (file.getName().equals(session.getId())) {
                        FileUtils.forceDelete(file);
                        break;
                    }
                }

            }

            HierarchyImplString.setWholeDictionary(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Enumeration<String> allAttributes = session.getAttributeNames();
        while (allAttributes.hasMoreElements()) {
            String attrName = allAttributes.nextElement();
            session.removeAttribute(attrName);
        }

        System.gc();


    }

    // ean to kanw kai gia hierarchies tha prepei na allaksw ta paths
    @PostMapping(value = "/action/uploadf")
    public @ResponseBody
    ErrorMessage uploadf(MultipartHttpServletRequest request, HttpSession session) {
        try {
            ErrorMessage errMes = new ErrorMessage();
            String uploadedFile;
            MultipartFile file = null;
            String filename = null;
            File dir;
            String input = (String) session.getAttribute("inputpath");
            if (os.equals("online")) {
                dir = new File(rootPath + File.separator + "amnesia" + File.separator + session.getId());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            } else {
                if (input == null) {
                    File f;
                    File dir1;
                    String rootPath;

                    if (!os.equals("linux")) {
                        f = new File(System.getProperty("java.class.path"));//linux
                        dir1 = f.getAbsoluteFile().getParentFile();
                        rootPath = dir1.toString();
                    } else {
                        rootPath = System.getProperty("user.home");//windows
                    }
                    dir = new File(rootPath + File.separator + "amnesiaResults" + File.separator + session.getId());
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                } else {

                    dir = new File(input);
                }
            }


            Iterator<String> itr = request.getFileNames();

            while (itr.hasNext()) {
                uploadedFile = itr.next();
                file = request.getFile(uploadedFile);
                file.getContentType();
                filename = file.getOriginalFilename();

            }

            session.setAttribute("inputpath", dir.toString());
            session.setAttribute("filename", filename);

            try { // Create the file on server
                File serverFile = new File(dir.getAbsolutePath()
                        + File.separator + filename);

                InputStream fis = file.getInputStream();
                DataOutputStream dout = new DataOutputStream(new FileOutputStream(serverFile));
                byte[] buffer = new byte[1024 * 1000];

                int b;
                while ((b = fis.read(buffer)) >= 0) {
                    dout.write(buffer, 0, b);
                }
                errMes.setSuccess(true);
                errMes.setProblem("You successfully uploaded file=" + filename);

                return errMes;
            } catch (Exception e) {
                errMes.setSuccess(false);
                errMes.setProblem("You failed to upload " + file.getOriginalFilename() + " => " + e.getMessage());
                return errMes;
            }


        } catch (Exception e) {
            System.out.println("problem");
        }
        return null;
    }

    @PostMapping(value = "/action/errorhandle")
    public @ResponseBody
    void errorHandling(@RequestParam("error") String error, HttpSession session) throws IOException {
        if (os.equals("online")) {
            System.out.println("Error handling");
            File dir = new File(rootPath + File.separator + "amnesia" + File.separator + "errorLog");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File errorFile = new File(dir.getAbsolutePath() + File.separator + "error_" + session.getId() + ".txt");
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(errorFile.getAbsolutePath(), true), StandardCharsets.UTF_8));
            out.write(error + "\n\n\n\n\n");
            out.close();
        }
    }

    /**
     *
     */
    @PostMapping(value = "/action/upload")
    public @ResponseBody
    ErrorMessage upload(@RequestParam("file") MultipartFile file,
                        @RequestParam("data") boolean data,
                        HttpSession session) {

        ErrorMessage errMes = new ErrorMessage();
        if (!file.isEmpty()) {
            try {
                // Creating the directory to store file
                File dir;
                String input = (String) session.getAttribute("inputpath");
                if (os.equals("online")) {
                    dir = new File(rootPath + File.separator + "amnesia" + File.separator + session.getId());
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }

                    if (data) {
                        session.setAttribute("inputpath", dir.toString());
                        session.setAttribute("filename", file.getOriginalFilename());
                    } else {
                        session.setAttribute("inputpath", dir.toString());
                    }
                } else {
                    if (input == null) {

                        File f;
                        File dir1;
                        String rootPath;

                        if (os.equals("linux")) {
                            f = new File(System.getProperty("java.class.path"));//linux
                            dir1 = f.getAbsoluteFile().getParentFile();
                            rootPath = dir1.toString();
                        } else {
                            rootPath = System.getProperty("user.home");//windows
                        }
                        dir = new File(rootPath + File.separator + "amnesiaResults" + File.separator + session.getId());
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }

                        if (data) {
                            session.setAttribute("inputpath", dir.toString());
                            session.setAttribute("filename", file.getOriginalFilename());
                        } else {
                            session.setAttribute("inputpath", dir.toString());
                        }
                    } else {
                        if (data) {
                            session.setAttribute("filename", file.getOriginalFilename());
                        }
                        dir = new File(input);
                    }
                }

                // Create the file on server
                File serverFile = new File(dir.getAbsolutePath()
                        + File.separator + file.getOriginalFilename());

                InputStream fis = file.getInputStream();
                DataOutputStream dout = new DataOutputStream(new FileOutputStream(serverFile));
                byte[] buffer = new byte[1024 * 1000];

                int b;
                while ((b = fis.read(buffer)) >= 0) {
                    dout.write(buffer, 0, b);
                }

                dout.close();
                fis.close();
                errMes.setSuccess(true);
                errMes.setProblem("You successfully uploaded file=" + file.getOriginalFilename());

                return errMes;
            } catch (OutOfMemoryError e) {

                errMes.setSuccess(false);
                errMes.setProblem("Memory problem");
                return errMes;
            } catch (Exception e) {
                errMes.setSuccess(false);
                errMes.setProblem("You failed to upload " + file.getOriginalFilename() + " => " + e.getMessage());
                return errMes;
            }
        } else {
            errMes.setSuccess(false);
            errMes.setProblem("You failed to upload " + file.getOriginalFilename() + "because the file was empty.");
            return errMes;
        }
    }

    @JsonView(View.SmallDataSet.class)
    @PostMapping(value = "/action/getsmalldataset")
    public @ResponseBody
    Data getSmallDataSet(@RequestParam("del") String del,
                         @RequestParam("datatype") String datatype,
                         @RequestParam("delset") String delset, HttpSession session) throws
            IOException,
            LimitException,
            DateParseException,
            NotFoundValueException {

        Data data = null;
        FileInputStream fstream;
        DataInputStream in;
        BufferedReader br;
        String strLine;
        String delimeter;
        String result = null;
        DictionaryString dict;

        String rootPath = (String) session.getAttribute("inputpath");
        String filename = (String) session.getAttribute("filename");
        File dir = new File(rootPath);
        String fullPath = dir + File.separator + filename;

        dict = HierarchyImplString.getWholeDictionary();
        if (dict == null) {
            System.out.println("Whole Dictionary is null");
            dict = new DictionaryString();
        }
        switch (datatype) {
            case "tabular": {

                if (del == null) {
                    delimeter = ",";
                } else {
                    delimeter = del;
                }

                System.out.println("del " + del);

                fstream = new FileInputStream(fullPath);
                in = new DataInputStream(fstream);
                br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                if (!fullPath.toLowerCase().endsWith(".xml")) {
                    while ((strLine = br.readLine()) != null) {
                        if (strLine.contains(delimeter)) {
                            data = new TXTData(fullPath, delimeter, dict);
                        } else {
                            if (br.readLine() != null) {
                                data = new TXTData(fullPath, delimeter, dict);
                            }
                        }
                        result = data.findColumnTypes();
                    }

                    br.close();
                } else {
                    data = new XMLData(fullPath, dict);
                    result = data.findColumnTypes();
                }

                if (result == null) {
                    return null;
                } else if (result.equals("1")) {
                    return data;
                }

                String[][] smallDataset = data.getSmallDataSet();

                data.getTypesOfVariables(smallDataset);

                break;
            }
            case "set":
                data = new SETData(fullPath, del, dict);
                data.readDataset(null, null);
                break;
            case "RelSet": {
                data = new RelSetData(fullPath, del, delset, dict);

                String[][] smallDataset = data.getSmallDataSet();

                data.getTypesOfVariables(smallDataset);
                break;
            }
            case "Disk": {
                data = new DiskData(fullPath, del, dict);

                result = data.findColumnTypes();

                if (result.equals("1")) {
                    return data;
                }

                String[][] smallDataset = data.getSmallDataSet();
                data.getTypesOfVariables(smallDataset);
                break;
            }
        }


        session.setAttribute("data", data);

        return data;
    }

    @PostMapping(value = "/action/deletefiles")
    public @ResponseBody
    void deleteFiles(HttpSession session) throws IOException {

        String inputPath = (String) session.getAttribute("inputpath");
        int index = inputPath.lastIndexOf('/');
        if (index == -1) {
            index = inputPath.lastIndexOf('\\');
        }
        File dir = new File(inputPath.substring(0, index));
        for (File file : dir.listFiles()) {
            System.out.println("Filename to delette " + file.getName() + " session " + session);
            long diff = new Date().getTime() - file.lastModified();
            System.out.println("diff " + diff + " thresehold " + (24 * 60 * 60 * 1000) + " last mod " + file.lastModified() + " date " + new Date(file.lastModified()).getDay());
            if (file.getName().equals(session.getId())) {
                boolean deleteDir = true;
                for (File sessionFile : file.listFiles()) {
                    if (!sessionFile.getName().endsWith(".xml") && !sessionFile.getName().endsWith(".db")) {
                        sessionFile.delete();
                    } else {
                        deleteDir = false;
                    }
                }
                if (deleteDir) {
                    try {
                        FileUtils.forceDelete(file);
                    } catch (Exception e) {
                        this.errorHandling(e.getMessage(), session);
                    }
                }
            } else if (!file.getName().equals("errorLog") && diff > 2 * 60 * 60 * 1000) {
                FileUtils.forceDelete(file);
            }
        }
    }

    @PostMapping("/action/createinputpath")
    public @ResponseBody
    void createInputPath(@RequestParam("path") String path) {
        File tempFile = new File(path);
        if (!tempFile.exists()) {
            tempFile.mkdirs();
        }
    }

    @PostMapping("/action/getexampledataset")
    public @ResponseBody
    String[] getExampleDataSet(HttpSession session) throws IOException {
        String[] exampleDataSet = new String[4];
        FileInputStream fstream;
        DataInputStream in;
        BufferedReader br;
        String strLine;
        String rootPath = (String) session.getAttribute("inputpath");
        String filename = (String) session.getAttribute("filename");
        int counter = 0;

        File dir = new File(rootPath);

        String fullPath = dir + File.separator + filename;
        fstream = new FileInputStream(fullPath);
        in = new DataInputStream(fstream);
        br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        while ((strLine = br.readLine()) != null) {
            if (counter < 4) {
                exampleDataSet[counter] = strLine;
                counter++;
            } else {
                break;
            }
        }
        br.close();
        fstream.close();
        in.close();
        return exampleDataSet;
    }

    @JsonView(View.DataSet.class)
    @PostMapping("/action/getdataset")
    public @ResponseBody
    Data getDataSet(@RequestParam("start") int start,
                    @RequestParam("length") int length,
                    HttpSession session) throws IOException {
        Data data = (Data) session.getAttribute("data");
        if (data == null) {
            System.out.println("data is null");
        } else {
            data.getPage(start, length);
        }
        return data;

    }

    @JsonView(View.GetColumnNames.class)
    @PostMapping("/action/getcolumnnames")
    public @ResponseBody
    Data getColumnNames(HttpSession session) throws IOException {

        Data data = (Data) session.getAttribute("data");
        if (data == null) {
            System.out.println("data is null col");
        }
        return data;
    }

    @PostMapping("/action/loaddataset")
    public @ResponseBody
    String loadDataset(@RequestParam("vartypes") String[] vartypes,
                       @RequestParam("checkColumns") boolean[] checkColumns,
                       HttpSession session) throws IOException, LimitException, DateParseException, NotFoundValueException {
        String result = null;
        Data data = (Data) session.getAttribute("data");
        if (vartypes != null) {
            result = data.readDataset(vartypes, checkColumns);
        }

        if (os.equals("online")) {
            this.deleteFiles(session);
        }
        return result;
    }

    @PostMapping("/action/savemask")
    public @ResponseBody
    void saveMask(@RequestParam("column") int column,
                  @RequestParam("positions") String positions,
                  @RequestParam("char") String character,
                  HttpSession session) {
        int[] pos_arr = Arrays.stream(positions.substring(1, positions.length() - 1).split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
        Data data = (Data) session.getAttribute("data");
        data.setMask(column, pos_arr, character.charAt(0));
    }

    @PostMapping("/action/saveselectedhier")
    public @ResponseBody
    void saveSelectedHier(@RequestParam("hiername") String hiername,
                          HttpSession session) {
        session.setAttribute("selectedhier", hiername);
    }

    @PostMapping("/action/getselectedhier")
    public @ResponseBody
    String getSelectedHier(HttpSession session) {
        return (String) session.getAttribute("selectedhier");
    }

    @PostMapping("/action/removehierarchy")
    public @ResponseBody
    String removeHierarchy(HttpSession session) {
        String hiername = (String) session.getAttribute("selectedhier");
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        if (!hierarchies.containsKey(hiername)) {
            return "no";
        }

        hierarchies.remove(hiername);
        if (hierarchies.size() > 0) {
            session.setAttribute("selectedhier", hierarchies.entrySet().iterator().next().getKey());
        } else {
            session.setAttribute("selectedhier", "");
        }
        return "OK";
    }

    @PostMapping("/action/loadhierarchy")
    public @ResponseBody
    String loadHierarcy(@RequestParam("filename") String filename, HttpSession session) throws IOException, LimitException {
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");


        if (hierarchies == null) {
            hierarchies = new HashMap<>();
            session.setAttribute("hierarchies", hierarchies);
        }
        String rootPath = (String) session.getAttribute("inputpath");
        this.createInputPath(rootPath);
        File dir = new File(rootPath);


        String fullPath = dir + File.separator + filename;


        Hierarchy h;

        //read metadata of file to determine which type of hierarchy to use
        List<String> results = findHierarchyType(fullPath);

        boolean distinct = false;
        String type = null;

        for (String res : results) {
            if (res.equalsIgnoreCase("distinct")) {
                distinct = true;
            }
            if (res.equalsIgnoreCase("int") || res.equalsIgnoreCase("decimal") || res.equalsIgnoreCase("string") || res.equalsIgnoreCase("date") || res.equalsIgnoreCase("double")) {
                type = res.replace("double", "decimal");
            }
        }

        if (results.isEmpty()) {
            System.out.println("results = empty");
        }
        //create distinct hierarchy according to type
        if (distinct) {
            if (type.equalsIgnoreCase("string")) {
                Data data = (Data) session.getAttribute("data");
                if (data != null) {
                    h = new HierarchyImplString(fullPath, data.getDictionary());
                } else {
                    DictionaryString dict = new DictionaryString();
                    if (hierarchies != null) {
                        for (Map.Entry<String, Hierarchy> entry : hierarchies.entrySet()) {
                            Hierarchy hTemp = entry.getValue();
                            if (hTemp instanceof HierarchyImplString) {
                                if (hTemp.getDictionaryData().getMaxUsedId() > dict.getMaxUsedId()) {
                                    dict = hTemp.getDictionaryData();
                                }
                            }

                        }
                    }
                    h = new HierarchyImplString(fullPath, dict);
                }
            } else if (type.equalsIgnoreCase("date")) {
                return "error";
            } else {
                h = new HierarchyImplDouble(fullPath);
            }
        } else {      //create range hierarchy
            if (type.equalsIgnoreCase("date")) {
                h = new HierarchyImplRangesDate(fullPath);
            } else {
                h = new HierarchyImplRangesNumbers(fullPath);
            }
        }
        h.load();
        session.setAttribute("selectedhier", h.getName());
        hierarchies.put(h.getName(), h);

        if (os.equals("online")) {
            this.deleteFiles(session);
        }

        return "OK";
    }

    @PostMapping("/action/savehierarchy")
    public @ResponseBody
    String saveHierarchy(HttpServletResponse response, HttpSession session) throws IOException {
        Hierarchy h;
        String hierName = (String) session.getAttribute("selectedhier");

        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        h = hierarchies.get(hierName);
        String inputPath = (String) session.getAttribute("inputpath");
        this.createInputPath(inputPath);

        File file = new File(inputPath + File.separator + hierName + ".txt");
        h.export(file.getAbsolutePath());
        InputStream myStream = new FileInputStream(file);


        // Set the content type and attachment header.
        if (h.getHierarchyType().equals("distinct")) {
            response.addHeader("Content-disposition", "attachment;filename=distinct_hier_" + file.getName());
            response.setContentType("txt/plain");
        } else {
            response.addHeader("Content-disposition", "attachment;filename=range_hier_" + file.getName());
            response.setContentType("txt/plain");
        }

        // Copy the stream to the response's output stream.
        IOUtils.copy(myStream, response.getOutputStream());
        response.flushBuffer();
        if (os.equals("online")) {
            this.deleteFiles(session);
        }
        return null;
    }

    @PostMapping("/action/autogeneratehierarchy")
    public @ResponseBody
    String autogeneratehierarchy(@RequestParam("typehier") String typehier,
                                 @RequestParam("vartype") String vartype,
                                 @RequestParam("onattribute") int onattribute,
                                 @RequestParam("step") double step,
                                 @RequestParam("sorting") String sorting,
                                 @RequestParam("hiername") String hiername,
                                 @RequestParam("fanout") int fanout,
                                 @RequestParam("limits") String limits,
                                 @RequestParam("months") int months,
                                 @RequestParam("days") int days,
                                 @RequestParam("years") int years,
                                 @RequestParam("length") int length,
                                 HttpSession session) throws LimitException {
        Map<String, Hierarchy> hierarchies;
        hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        Hierarchy h = null;


        if (hierarchies == null) {
            hierarchies = new HashMap<>();
            session.setAttribute("hierarchies", hierarchies);
        }

        Data data = (Data) session.getAttribute("data");

        String attribute = data.getColumnByPosition(onattribute);

        if (typehier.equals("mask")) {
            System.out.println("autogenerate backend " + step + " " + typehier + " " + vartype + " " + onattribute + " " + sorting + " " + hiername + " " + fanout + " " + limits + " " + length);
            h = new AutoHierarchyImplMaskString(hiername, vartype, "distinct", attribute, data, length);
        } else if (typehier.equals("distinct")) {
            if (vartype.equals("int") || vartype.equals("double")) {
                h = new AutoHierarchyImplDouble(hiername, vartype, "distinct", attribute, sorting, fanout, data);

            }
            if (vartype.equals("date")) {
                h = new AutoHierarchyImplDate(hiername, vartype, "distinct", attribute, data);
            } else if (vartype.equals("string")) {
                h = new AutoHierarchyImplString(hiername, vartype, "distinct", attribute, sorting, fanout, data);
            }
        } else {
            if (vartype.equals("date")) {
                String[] temp = null;
                temp = limits.split("-");
                int start = Integer.parseInt(temp[0]);
                int end = Integer.parseInt(temp[1]);
                h = new AutoHierarchyImplRangesDate(hiername, vartype, "range", start, end, fanout, months, days, years);
            } else {
                String[] temp;
                Double start = null, end = null;
                temp = limits.split("-");
                int count = StringUtils.countMatches(limits, "-");
                if (count == 1) {
                    start = Double.parseDouble(temp[0]);
                    end = Double.parseDouble(temp[1]);
                } else if (count == 2) {
                    try {
                        start = Double.parseDouble("-" + temp[1]);
                        end = Double.parseDouble(temp[2]);
                        System.out.println("Count " + count + " start " + start + " end " + end);
                    } catch (Exception e) {
                        e.printStackTrace();

                        // TODO exception
                    }
                } else if (count == 3) {
                    try {
                        start = Double.parseDouble("-" + temp[1]);
                        end = Double.parseDouble("-" + temp[3]);
                        System.out.println("Count " + count + " start " + start + " end " + end);
                    } catch (Exception e) {
                        e.printStackTrace();

                        // TODO exception
                    }
                } else {
                    /// TODO exception

                    System.out.println("Count " + count);
                }
                System.out.println("info " + hiername + " " + vartype + " start " + start + " end " + end + " step " + step + " " + fanout);
                h = new AutoHierarchyImplRangesNumbers2(hiername, vartype, "range", start, end, step, fanout);
            }
        }

        h.autogenerate();
        hierarchies.put(h.getName(), h);
        session.setAttribute("selectedhier", h.getName());
        return "OK";
    }

    @PostMapping("/action/gethiergraph")
    public @ResponseBody
    Graph getHierGraph(@RequestParam("hiername") String hierName,
                       @RequestParam("node") String node,
                       @RequestParam("level") int level,
                       HttpSession session) {
        Hierarchy h = null;
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");

        if (hierarchies != null) {
            h = hierarchies.get(hierName);
        }
        Graph nGraph = h.getGraph(node, level);
        session.setAttribute("selectedhier", hierName);
        return nGraph;

    }

    @PostMapping("/action/gethierarchies")
    public @ResponseBody
    HierToJson[] getHierarchies(@RequestParam("selectedhier") String selectedHier,
                                HttpSession session) {
        HierToJson[] hierArray = null;
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");

        if (selectedHier.equals("null")) {
            if (hierarchies != null) {
                hierArray = new HierToJson[hierarchies.size()];
                int i = 0;
                for (Map.Entry<String, Hierarchy> entry : hierarchies.entrySet()) {
                    hierArray[i] = new HierToJson(entry.getKey(), entry.getKey(), entry.getKey(), entry.getValue().getNodesType());
                    i++;
                }
            }
        } else {
            if (hierarchies != null) {
                hierArray = new HierToJson[hierarchies.size()];

                hierArray[0] = new HierToJson(selectedHier, selectedHier);
                int k = 0;
                int j = 1;

                for (Map.Entry<String, Hierarchy> entry : hierarchies.entrySet()) {
                    if (!entry.getKey().equals(selectedHier)) {
                        hierArray[j] = new HierToJson(entry.getKey(), entry.getKey());
                        j++;
                    }

                    hierArray[k].setSort(entry.getKey());
                    hierArray[k].setType(entry.getValue().getNodesType());

                    k++;
                }
            }

        }
        return hierArray;
    }

    @JsonView(View.GetDataTypes.class)
    @PostMapping("/action/getattrtypes")
    public @ResponseBody
    Data getAttrTypes(HttpSession session) {
        return (Data) session.getAttribute("data");
    }

    @PostMapping("/action/gethiertypes")
    public @ResponseBody
    HierToJson[] getHierTypes(HttpSession session) {


        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        HierToJson[] hierArray = new HierToJson[hierarchies.size()];

        int i = 0;
        for (Map.Entry<String, Hierarchy> entry : hierarchies.entrySet()) {
            Hierarchy h = entry.getValue();
            hierArray[i] = new HierToJson(entry.getKey(), h.getNodesType());
            i++;
        }


        return hierArray;
    }

    @PostMapping("/action/algorithmexecution")
    public @ResponseBody
    String algorithmExecution(@RequestParam("k") int k,
                              @RequestParam("m") int m,
                              @RequestParam("algo") String algo,
                              @RequestParam("relations") String[] relations,
                              HttpSession session) {


        Algorithm algorithm = null;
        Map<Integer, Hierarchy> quasiIdentifiers = new HashMap<Integer, Hierarchy>();
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        Data data = (Data) session.getAttribute("data");

        for (int i = 0; i < relations.length; i++) {
            if (!relations[i].equals("")) {
                quasiIdentifiers.put(i, hierarchies.get(relations[i]));
            }
        }


        ///////////////////////new feature///////////////////////
        String checkHier;
        for (Map.Entry<Integer, Hierarchy> entry : quasiIdentifiers.entrySet()) {
            Hierarchy h = entry.getValue();
            System.out.println("h " + h + " htype " + h.getHierarchyType());
            checkHier = h.checkHier(data, entry.getKey());
            if (checkHier != null && !checkHier.endsWith("Ok")) {
                return checkHier;
            }
        }

        Map<String, Integer> args = new HashMap<>();
        switch (algo) {
            case "Flash":
                args.put("k", k);
                algorithm = new Flash();
                session.setAttribute("algorithm", "flash");

                break;
            case "pFlash":
                args.put("k", k);
                algorithm = new ParallelFlash();
                session.setAttribute("algorithm", "flash");
                break;
            case "clustering":
                args.put("k", k);
                algorithm = new ClusterBasedAlgorithm();
                session.setAttribute("algorithm", "clustering");
                break;
            case "kmAnonymity":
            case "apriori":
            case "AprioriShort":
            case "mixedapriori":
                args.put("k", k);
                if (k > data.getDataLenght()) {
                    //ErrorWindow.showErrorWindow("Parameter k should be less or equal to the dataset length");
                    //return;
                }
                args.put("m", m);
                if (algo.equals("apriori")) {
                    if (!(data instanceof SETData)) {
                    }
                    algorithm = new Apriori();
                    quasiIdentifiers.get(0).buildDictionary(quasiIdentifiers.get(0).getDictionary());
                } else if (algo.equals("mixedapriori")) {
                    algorithm = new MixedApriori();
                }
                session.setAttribute("algorithm", "apriori");
                break;
        }


        algorithm.setDataset(data);
        algorithm.setHierarchies(quasiIdentifiers);

        algorithm.setArguments(args);
        final String message = "memory problem";
        String resultAlgo = "";
        Future<String> future = null;
        System.out.println("Algorithm starts");
        try {
            if (os.equals("online")) {
                ExecutorService executor = Executors.newCachedThreadPool();
                final Algorithm temp = algorithm;
                future = executor.submit(() -> {
                    try {
                        temp.anonymize();
                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();
                        return message;
                    }


                    return "Ok";
                });
                resultAlgo = future.get(3, TimeUnit.MINUTES);
            } else {
                algorithm.anonymize();
            }

        } catch (TimeoutException e) {
            // Too long time
            e.printStackTrace();
            future.cancel(true);
            restart(session);
            return "outoftime";
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return message;
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
            Logger.getLogger(AppCon.class.getName()).log(Level.SEVERE, null, ex);
            restart(session);
            return "wrong";
        }

        if (resultAlgo.equals(message)) {
            return message;
        }


        session.setAttribute("quasiIdentifiers", quasiIdentifiers);
        session.setAttribute("k", k);

        if (algorithm.getResultSet() == null) {
            if (!algo.equals("clustering")) {
                return "noresults";
            }
        } else {
            session.setAttribute("results", algorithm.getResultSet());
            if (!algo.equals("apriori") && !algo.equals("mixedapriori") && !algo.equals("clustering")) {
                Graph graph = algorithm.getLattice();
                session.setAttribute("graph", graph);
            }
        }
        return "ok";
    }

    @GetMapping("/action/informationloss")
    public @ResponseBody
    String InformationLoss(HttpSession session) {
        Set<LatticeNode> infoLossFirstStep = new HashSet<>();
        Set<LatticeNode> infoLossSecondStep = new HashSet<>();
        int minSum = 0;
        int[] minHierArray;
        int minHier;
        LatticeNode solution = null;
        String solutionStr;

        boolean FLAG = false;

        Set<LatticeNode> results = (Set<LatticeNode>) session.getAttribute("results");
        for (LatticeNode n : results) {
            if (!FLAG) {
                minSum = n.getLevel();
                FLAG = true;
            } else {
                if (minSum > n.getLevel()) {
                    minSum = n.getLevel();
                }
            }
        }

        for (LatticeNode n : results) {
            if (minSum == n.getLevel()) {
                infoLossFirstStep.add(n);
            }
        }

        //second step, min max hierarchy
        minHierArray = new int[infoLossFirstStep.size()];
        int counter = 0;

        minHier = Ints.min(minHierArray);

        for (LatticeNode n : infoLossFirstStep) {
            int[] temp = n.getArray();
            if (minHier == Ints.max(temp)) {
                infoLossSecondStep.add(n);
            }
        }

        //third step, choose the first one
        for (LatticeNode n : infoLossSecondStep) {
            solution = n;
            break;
        }

        solutionStr = solution.toString();
        solutionStr = solutionStr.replace("[", "");
        solutionStr = solutionStr.replace("]", "");
        solutionStr = solutionStr.replace(" ", "");
        return solutionStr;
    }

    @GetMapping("/action/getsolutiongraph")
    public @ResponseBody
    Graph getSolGraph(HttpSession session) {
        Graph graph = (Graph) session.getAttribute("graph");
        System.out.println("Return solutions");
        return graph;

    }

    @PostMapping("/action/getanondataset")
    public @ResponseBody
    AnonymizedDataset getAnonDataSet(@RequestParam("start") int start,
                                     @RequestParam("length") int length,
                                     HttpSession session) throws Exception {
        AnonymizedDataset anonData = null;
        String selectedNode = (String) session.getAttribute("selectednode");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        Data data = (Data) session.getAttribute("data");
        Map<Integer, Set<String>> toSuppress = (Map<Integer, Set<String>>) session.getAttribute("tosuppress");
        Map<String, Map<String, String>> allRules = (Map<String, Map<String, String>>) session.getAttribute("anonrules");

        Map<String, Set<String>> toSuppressJson = null;
        StringBuilder selectedAttrNames = null;
        boolean FLAG = false;


        //if (selectedNode!= null){
        if (allRules == null) {
            //System.out.println("get anon dataset to suppress = " + toSuppress);
            if (toSuppress != null) {
                toSuppressJson = new HashMap<>();
                for (Map.Entry<Integer, Set<String>> entry : toSuppress.entrySet()) {
                    if (entry.getKey().toString().equals("-1")) {
                        for (Map.Entry<Integer, Hierarchy> entry1 : quasiIdentifiers.entrySet()) {
                            if (!FLAG) {
                                FLAG = true;
                                selectedAttrNames = new StringBuilder(data.getColumnByPosition(entry1.getKey()));
                            } else {
                                selectedAttrNames.append(",").append(data.getColumnByPosition(entry1.getKey()));

                            }
                        }
                        toSuppressJson.put(selectedAttrNames.toString(), entry.getValue());
                    } else {
                        selectedAttrNames = new StringBuilder(data.getColumnByPosition(entry.getKey()));
                        toSuppressJson.put(selectedAttrNames.toString(), entry.getValue());
                    }
                }
            } else {
                for (Map.Entry<Integer, Hierarchy> entry : quasiIdentifiers.entrySet()) {
                    if (!FLAG) {
                        FLAG = true;
                        selectedAttrNames = new StringBuilder(data.getColumnByPosition(entry.getKey()));
                    } else {
                        selectedAttrNames.append(",").append(data.getColumnByPosition(entry.getKey()));

                    }
                }

            }

        }

        if (data == null) {
            System.out.println("data is null");
        } else {
            anonData = new AnonymizedDataset(data, start, length, selectedNode, quasiIdentifiers, toSuppress, selectedAttrNames.toString(), toSuppressJson);
            if (!data.getClass().toString().contains("SET") && !data.getClass().toString().contains("RelSet") && !data.getClass().toString().contains("Disk")) {
                System.out.println("action/getanondataset TXT ===========");
                if (allRules == null) {
                    anonData.renderAnonymizedTable();
                } else {
                    anonData.anonymizeWithImportedRules(allRules, null);
                }
            } else if (data.getClass().toString().contains("SET")) {
                Map<Double, Double> rules = (Map<Double, Double>) session.getAttribute("results");
                if (allRules == null) {
                    anonData.renderAnonymizedTable(rules, quasiIdentifiers.get(0).getDictionary());
                } else {
                    anonData.anonymizeSETWithImportedRules(allRules, null);
                }
            } else if (data.getClass().toString().contains("Disk")) {
                System.out.println("action/getanondataset Disk ===========");
                if (allRules == null) {
                    anonData.renderAnonymizedDiskTable();
                } else {
                    anonData.anonymizeWithImportedRulesForDisk(allRules, null);
                }
            } else {
                System.out.println("action/getanondataset Mixed ===========");

                Map<Integer, Map<Object, Object>> rules = (Map<Integer, Map<Object, Object>>) session.getAttribute("results");
                if (allRules == null) {
                    anonData.renderAnonymizedTable(rules);
                } else {
                    anonData.anonymizeRelSetWithImportedRules(allRules, null);
                }

            }
            session.setAttribute("anondata", anonData);
        }

        return anonData;

    }

    @PostMapping("/action/savedataset")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public @ResponseBody
    void saveDataset(HttpServletRequest request,
                     HttpSession session,
                     HttpServletResponse response) throws IOException {

        if (request != null) {
            System.out.println("app export datasettttttt");
            ServletContext context = request.getServletContext();
            String appPath = context.getRealPath("");
            System.out.println("appPath = " + appPath);
        }

        Data data = (Data) session.getAttribute("data");
        String filename = (String) session.getAttribute("filename");
        String inputPath = (String) session.getAttribute("inputpath");

        this.createInputPath(inputPath);

        System.out.println("Export Dataset... " + filename);
        System.out.println("Export Dataset...");

        File file = new File(inputPath + File.separator + filename);
        file.createNewFile();
        // Get your file stream from wherever.
        data.exportOriginalData();
        if (response != null) {
            InputStream myStream = new FileInputStream(inputPath + File.separator + "anonymized_files.zip");

            // Set the content type and attachment header.
            response.addHeader("Content-disposition", "attachment;filename=anonymized_files.zip");
            response.setContentType("application/zip");

            // Copy the stream to the response's output stream.
            IOUtils.copy(myStream, response.getOutputStream());
            response.flushBuffer();
            myStream.close();
            if (os.equals("online")) {
                this.deleteFiles(session);
            }
        }
    }

    @PostMapping("/action/saveanonymizedataset")
    public @ResponseBody
    void saveAnonymizeDataset(HttpSession session, HttpServletResponse response) throws IOException {

        Data data = (Data) session.getAttribute("data");
        String filename = (String) session.getAttribute("filename");
        String inputPath = (String) session.getAttribute("inputpath");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        Map<String, Map<String, String>> allRules = (Map<String, Map<String, String>>) session.getAttribute("anonrules");

        this.createInputPath(inputPath);


//        System.out.println("Export Anonymized Dataset... " + filename);
        AnonymizedDataset anonData = (AnonymizedDataset) session.getAttribute("anondata");
        anonData.setStart(0);
//        System.out.println("Export anonymizedDataset...");
        File file = new File(inputPath + File.separator + "anonymized_" + filename);

        if (data.getClass().toString().contains("SET")) {
                anonData.setLength(data.getRecordsTotal());
                anonData.anonymizeSETWithImportedRules(allRules, file.getAbsolutePath());
        } else if (data.getClass().toString().contains("RelSet")) {
                anonData.setLength(data.getRecordsTotal());
                anonData.anonymizeRelSetWithImportedRules(allRules, file.getAbsolutePath());

        } else if (data.getClass().toGenericString().contains("Disk")) {
                anonData.setLength(data.getRecordsTotal());
                anonData.anonymizeWithImportedRulesForDisk(allRules, file.getAbsolutePath());

        } else {
            System.out.println("Data length " + data.getRecordsTotal());
            anonData.setLength(data.getRecordsTotal());
            anonData.anonymizeWithImportedRules(allRules, file.getAbsolutePath());
        }

        if (response != null) {

            InputStream myStream = new FileInputStream(file);

            // Set the content type and attachment header.
            response.addHeader("Content-disposition", "attachment;filename=" + file.getName());
            response.setContentType("txt/plain");

            // Copy the stream to the response's output stream.
            IOUtils.copy(myStream, response.getOutputStream());
            response.flushBuffer();
            myStream.close();

            if (os.equals("online")) {
                this.deleteFiles(session);
            }
        }
    }

    @PostMapping("/action/getdataversefiles")
    public @ResponseBody JSONObject getDataverseFiles(HttpSession session,
                                                      @RequestParam("usertoken") String usertoken,
                                                      @RequestParam("server_url") String server_url,
                                                      @RequestParam("dataset_id") String dataset_id) throws Exception {
        if (server_url.endsWith("/")) {
            server_url = server_url.substring(0, server_url.length() - 1);
        }
        List<DataverseFile> files = DataverseConnection.getDataverseFiles(server_url, usertoken, dataset_id);
        if (files == null) {
            return null;
        } else {
            JSONObject jdata = new JSONObject();
            session.setAttribute("dataversefiles", files);
            jdata.put("data", files);
            return jdata;
        }
    }

    @PostMapping("/action/getzenodofiles")
    public @ResponseBody ZenodoFilesToJson getZenodoFiles(HttpSession session,
                                                          @RequestParam("usertoken") String usertoken) throws IOException {
        ZenodoFilesToJson zenJson;
        //String usertoken1 = "cSQgGzD08dJ11RMyRzLRhU4hi57LK454T8sovlw6Z2STZrQbzg809wUt6ywt";
        Map<Integer, ZenodoFile> files = ZenodoConnection.getDepositionFiles(usertoken);
        if (files == null) {
            return null;
        } else {
            zenJson = new ZenodoFilesToJson(files, false, null, null, null, null);
            session.setAttribute("zenodofiles", files);
        }
        return zenJson;

    }

    @PostMapping("/action/loaddataversefile")
    public @ResponseBody String loaddataversefile(HttpSession session,
                             @RequestParam("filename") String fileName,
                             @RequestParam("type") String type,
                             @RequestParam("size") String filesize,
                             @RequestParam("usertoken") String usertoken,
                             @RequestParam("server_url") String server_url
    ) throws IOException {

        if (server_url.endsWith("/")) {
            server_url = server_url.substring(0, server_url.length() - 1);
        }

        List<DataverseFile> files = (List<DataverseFile>) session.getAttribute("dataversefiles");
        String inputPath = (String) session.getAttribute("inputpath");
        File dir1, f1, dir = null;
        String rootPath;

        if (os.equals("linux")) {
            f1 = new File(System.getProperty("java.class.path"));//linux
            dir1 = f1.getAbsoluteFile().getParentFile();
            rootPath = dir1.toString();
        } else {
            rootPath = System.getProperty("user.home");//windows
        }


        if (os.equals("online")) {
            dir = new File(this.rootPath + File.separator + "amnesia" + File.separator + session.getId());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            inputPath = this.rootPath + File.separator + "amnesia" + File.separator + session.getId();
        } else {
            dir = new File(rootPath + File.separator + "amnesiaResults" + File.separator + session.getId());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            inputPath = rootPath + File.separator + "amnesiaResults" + File.separator + session.getId();
        }
        session.setAttribute("inputpath", inputPath);
        session.setAttribute("filename", fileName);

        for (DataverseFile f : files) {
            if (f.getFileName().equals(fileName) && f.getType().equals(type) && f.getFilesize().equals(filesize)) {
                DataverseConnection.downloadFile(server_url, usertoken, inputPath, f);
                break;
            }
        }
        return null;
    }

    @PostMapping("/action/loadzenodofile")
    public @ResponseBody String loadzenodofile(HttpSession session,
                          @RequestParam("filename") String fileName,
                          @RequestParam("title") String title,
                          @RequestParam("usertoken") String usertoken) throws IOException {
        ZenodoFile zenFile = null;

        Map<Integer, ZenodoFile> files = (Map<Integer, ZenodoFile>) session.getAttribute("zenodofiles");
        String inputPath = (String) session.getAttribute("inputpath");
        File dir1, f1, dir;
        String rootPath;

        if (os.equals("linux")) {
            f1 = new File(System.getProperty("java.class.path"));//linux
            dir1 = f1.getAbsoluteFile().getParentFile();
            rootPath = dir1.toString();
        } else {
            rootPath = System.getProperty("user.home");//windows
        }
        if (inputPath == null) {
            if (os.equals("online")) {
                dir = new File(this.rootPath + File.separator + "amnesia" + File.separator + session.getId());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                inputPath = this.rootPath + File.separator + "amnesia" + File.separator + session.getId();
            } else {
                dir = new File(rootPath + File.separator + "amnesiaResults" + File.separator + session.getId());
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                inputPath = rootPath + File.separator + "amnesiaResults" + File.separator + session.getId();
            }
            session.setAttribute("inputpath", inputPath);
            session.setAttribute("filename", fileName);

        }
        for (Map.Entry<Integer, ZenodoFile> entry : files.entrySet()) {
            zenFile = entry.getValue();
            if (zenFile.getFileName().equals(fileName)) {
                if (zenFile.getTitle().equals(title)) {
                    break;
                }
            }
        }
        ZenodoConnection.downloadFile(zenFile, inputPath + File.separator + zenFile.getFileName(), usertoken);
        return null;
    }

    @PostMapping("/action/getsimilarzenodofiles")
    public @ResponseBody ZenodoFilesToJson getSimilarZenodoFiles(HttpSession session,
                                            @RequestParam("usertoken") String usertoken,
                                            @RequestParam("filename") String fileName,
                                            @RequestParam("title") String title,
                                            @RequestParam("keywords") String keywords) throws IOException {
        System.out.println("Zenodo Files");
        ZenodoFilesToJson zenJson;
        String inputPath = (String) session.getAttribute("inputpath");
        String fileNameInput = (String) session.getAttribute("filename");
        File dir = new File(inputPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        inputPath = inputPath + File.separator + fileNameInput;


        Map<Integer, ZenodoFile> files = ZenodoConnection.getDepositionFiles(usertoken);
        if (files == null) {
            return null;
        } else {
            Data data = (Data) session.getAttribute("data");
            data.exportOriginalData();
            zenJson = new ZenodoFilesToJson(files, true, fileName, title, keywords, inputPath);
            session.setAttribute("zenodofiles", files);
        }


        return zenJson;
    }

    @PostMapping("/action/saveurltoreturn")
     @ResponseBody
    public void saveUrlToReturn(HttpSession session,
                         @RequestParam("url") String url) {
        session.setAttribute("urltoreturn", url);
    }

    @PostMapping("/action/savefiletodataverse")
    @ResponseBody
    public String saveFileToDataverse(HttpSession session,
                               @RequestParam("usertoken") String usertoken,
                               @RequestParam("descr") String descr,
                               @RequestParam("server_url") String server_url,
                               @RequestParam("dataset_id") String dataset_id) throws IOException{

        if (server_url.endsWith("/")) {
            server_url = server_url.substring(0, server_url.length() - 1);
        }

        String url;
        url = (String) session.getAttribute("urltoreturn");

        String tempName = (String) session.getAttribute("filename");
        String inputPath = (String) session.getAttribute("inputpath");
        this.createInputPath(inputPath);

        String file;

        if (url.equals("mydataset.html")) {
            this.saveDataset(null, session, null);
            file = inputPath + File.separator + "anonymized_files.zip";
        } else {
            this.saveAnonymizeDataset(session, null);
            file = inputPath + File.separator + "anonymized_" + tempName;
        }

        DataverseConnection.uploadFile(server_url, usertoken, dataset_id, file, descr);
        return url;
    }

    @PostMapping("/action/savefiletozenodo")
    public @ResponseBody
    String saveFileToZenodo(HttpSession session, @RequestParam("usertoken") String usertoken,
                            @RequestParam("author") String author,
                            @RequestParam("affiliation") String affiliation,
                            @RequestParam("filename") String filename,
                            @RequestParam("title") String title,
                            @RequestParam("description") String description,
                            @RequestParam("contributors") String contributors,
                            @RequestParam("keywords") String keywords) throws IOException, ParseException, InterruptedException {

        String url = (String) session.getAttribute("urltoreturn");

        System.out.println("urare here = " + url);

        String tempName = (String) session.getAttribute("filename");
        String type = "dataset";
        String access = "open";
        String file;
        String inputPath = (String) session.getAttribute("inputpath");
        this.createInputPath(inputPath);
        //String filename = null;

        if (url.equals("mydataset.html")) {
            this.saveDataset(null, session, null);
            file = inputPath + File.separator + "anonymized_files.zip";
            System.out.println("edwwwwwwwwwwwwwwwww");
        } else {
            System.out.println("anonymizeeeeeeeeeeeeeeeeeeeeeeeeeeee");
            this.saveAnonymizeDataset(session, null);
            //filename = "anonymize" +tempName;
            file = inputPath + File.separator + "anonymized_" + tempName;
        }


        System.out.println("url = " + url);

        //crete deposition
        Long depositionId = ZenodoConnection.createDeposition(usertoken,
                title,
                type,
                description,
                author,
                affiliation,
                access,
                keywords);

        if (depositionId == null) {
            //Show error
            return ZenodoConnection.getErrorMessage();
        }

        System.out.println("depositionId = " + depositionId);

        //upload file to deposition
        if (!ZenodoConnection.uploadFileToDeposition(depositionId,
                file,
                filename,
                usertoken)) {
            //Show error
            return ZenodoConnection.getErrorMessage();
        }

        //publish deposition
        if (!ZenodoConnection.publishDeposition(depositionId, usertoken)) {
            return ZenodoConnection.getErrorMessage();
        }

        System.out.println("url = " + url);

        if (os.equals("online")) {
            this.deleteFiles(session);
        }

        return url;
    }

    @PostMapping("/action/getselectednode")
    public @ResponseBody
    String getSelectedNode(HttpSession session) {
        return (String) session.getAttribute("selectednode");
    }

    @PostMapping("/action/setselectednode")
    public @ResponseBody
    void setSelectedNode(HttpSession session, @RequestParam("selectednode") String selectedNode) {
        session.setAttribute("selectednode", selectedNode);
    }

    @PostMapping("/action/getsetdata")
    public @ResponseBody
    ErrorMessage getSetData(HttpSession session) {
        ErrorMessage errMes = new ErrorMessage();
        try {
            Data data;
            String rootPath = (String) session.getAttribute("inputpath");
            String filename = (String) session.getAttribute("filename");
            Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
            File dir = new File(rootPath);
            String fullPath = dir + File.separator + filename;
            DictionaryString dict = new DictionaryString();
            if (hierarchies != null) {
                for (Map.Entry<String, Hierarchy> entry : hierarchies.entrySet()) {
                    Hierarchy h = entry.getValue();
                    if (h instanceof HierarchyImplString) {
                        if (h.getDictionary().getMaxUsedId() > dict.getMaxUsedId()) {
                            dict = h.getDictionary();
                        }
                    }

                }
            }

            data = new SETData(fullPath, ",", dict);
            data.readDataset(null, null);
            session.setAttribute("data", data);
            errMes.setSuccess(true);
            errMes.setProblem("Set-valued dataset was successfully saved on server");
            return errMes;
        } catch (Exception e) {
            errMes.setSuccess(false);
            errMes.setProblem("Problem with set-valued dataset");
            return errMes;
        }

    }

    @PostMapping("/action/getpairranges")
    public @ResponseBody
    MyPair getPairRanges(@RequestParam("columnAttr") int columnAttr,
                         @RequestParam("vartype") String vartype,
                         HttpSession session) {
        Data data = (Data) session.getAttribute("data");
        MyPair p = vartype.equals("date") ? new MyPair(data, vartype) : new MyPair(data, null);
        p.findMin(columnAttr);

        return p;
    }

    @PostMapping("/action/addnodehier")
    public @ResponseBody
    String addNodeHier(@RequestParam("newnode") String newNode,
                       @RequestParam("parent") String parent,
                       @RequestParam("hiername") String hierName,
                       HttpSession session) throws ParseException, LimitException {
        Map<String, Hierarchy> hierarchies;
        Hierarchy h;


        hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");


        h = hierarchies.get(hierName);


        if (h.getNodesType().equals("string")) {
            int newStrId, parentId;

            if (newNode.equals("") || newNode.equals("(null)")) {
                newNode = "NaN";
            }
            DictionaryString dictData = h.getDictionaryData();
            DictionaryString dictHier = h.getDictionary();


            if (dictData.containsString(newNode)) {
                newStrId = dictData.getStringToId(newNode);
                if (h.getParent((double) newStrId) != null) {
                    return "The node exists in hierarchy";
                }
            } else if (dictHier.containsString(newNode)) {
                newStrId = dictHier.getStringToId(newNode);
                if (h.getParent((double) newStrId) != null) {
                    return "The node exists in hierarchy";
                }
            } else {
                if (dictData.isEmpty() && dictHier.isEmpty()) {
                    System.out.println("Both empty");
                    newStrId = 1;
                } else if (!dictData.isEmpty() && !dictHier.isEmpty()) {
                    System.out.println("Both have values");
                    if (dictData.getMaxUsedId() > dictHier.getMaxUsedId()) {
                        newStrId = dictData.getMaxUsedId() + 1;
                    } else {
                        newStrId = dictHier.getMaxUsedId() + 1;
                    }
                } else if (dictData.isEmpty()) {
                    System.out.println("Dict data empty");
                    newStrId = dictHier.getMaxUsedId() + 1;
                } else {
                    System.out.println("Dict hier empty");
                    newStrId = dictData.getMaxUsedId() + 1;
                }

                h.getDictionary().putIdToString(newStrId, newNode);
                h.getDictionary().putStringToId(newNode, newStrId);
            }

            if (dictData.containsString(parent)) {
                parentId = dictData.getStringToId(parent);
            } else {
                parentId = dictHier.getStringToId(parent);
            }
            h.add((double) newStrId, (double) parentId);

        } else {
            if (h.getHierarchyType().equals("range")) {
                String del = "-";

                if (newNode.contains(" ")) {
                    newNode = newNode.replaceAll(" ", "");
                }

                if (parent.contains(" ")) {
                    parent = parent.replaceAll(" ", "");
                }

                String[] tempNew = newNode.split(del);
                String[] tempParent = parent.split(del);


                if (h.getNodesType().equals("date")) {
                    RangeDate newNodeDate, parentNodeDate;

                    if (newNode.equals("(null)"))
                        newNodeDate = new RangeDate(null, null);
                    else if (tempNew.length == 2)
                        newNodeDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(tempNew[0], true), ((HierarchyImplRangesDate) h).getDateFromString(tempNew[1], false));
                    else
                        newNodeDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(newNode, true), ((HierarchyImplRangesDate) h).getDateFromString(newNode, false));

                    if (tempParent.length == 2)
                        parentNodeDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(tempParent[0], true), ((HierarchyImplRangesDate) h).getDateFromString(tempParent[1], false));
                    else
                        parentNodeDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(parent, true), ((HierarchyImplRangesDate) h).getDateFromString(parent, false));

                    if (h.getParent(newNodeDate) != null) {
                        return "The node exists in hierarchy";
                    }

                    h.add(newNodeDate, parentNodeDate);
                } else {

                    RangeDouble newNodeRange = RangeDouble.parseRange(newNode);
                    newNodeRange.setNodesType(h.getNodesType());
                    RangeDouble parentNodeRange = RangeDouble.parseRange(parent);
                    parentNodeRange.setNodesType(h.getNodesType());

                    if (h.getParent(newNodeRange) != null) {
                        return "The node exists in hierarchy";
                    }

                    h.add(newNodeRange, parentNodeRange);
                }
            } else {
                //// TODO add check intdouble existance
                if (newNode.equals("(null)")) {
                    if (h.getParent(Double.NaN) != null || h.getParent(2147483646.0) != null) {
                        return "The node exists in hierarchy";
                    }
                    h.add(2147483646.0, Double.parseDouble(parent));
                } else {
                    if (h.getParent(Double.parseDouble(newNode)) != null) {
                        return "The node exists in hierarchy";
                    }
                    h.add(Double.parseDouble(newNode), Double.parseDouble(parent));
                }
            }
        }

        return "ok";

    }

    @PostMapping("/action/editnodehier")
    public @ResponseBody
    String editNodeHier(@RequestParam("oldnode") String oldNode,
                        @RequestParam("newnode") String newNode,
                        @RequestParam("hiername") String hierName,
                        HttpSession session) throws ParseException {

        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");

        Hierarchy h = hierarchies.get(hierName);
        int newStrId, oldStrId;

        if (h.getNodesType().equals("string")) {
            if (newNode.trim().isEmpty()) {
                newNode = "NaN";
            }

            if (oldNode.trim().equals("(null)")) {
                oldNode = "NaN";
            }
            if (h.getDictionaryData().containsString(newNode)) {
                newStrId = h.getDictionaryData().getStringToId(newNode);
                if (h.getParent((double) newStrId) != null) {
                    return "Value " + newNode + " is already exists in hierarchy";
                }
            } else if (h.getDictionary().containsString(newNode)) {
                newStrId = h.getDictionary().getStringToId(newNode);
                if (h.getParent((double) newStrId) != null) {
                    return "Value " + newNode + " is already exists in hierarchy";
                }
            } else {
                if (h.getDictionaryData().isEmpty() && h.getDictionary().isEmpty()) {
                    System.out.println("Edit Both empty edit");
                    newStrId = 1;
                } else if (!h.getDictionaryData().isEmpty() && !h.getDictionary().isEmpty()) {
                    System.out.println("Both have values edit");
                    if (h.getDictionaryData().getMaxUsedId() > h.getDictionary().getMaxUsedId()) {
                        newStrId = h.getDictionaryData().getMaxUsedId() + 1;
                    } else {
                        newStrId = h.getDictionary().getMaxUsedId() + 1;
                    }
                } else if (h.getDictionaryData().isEmpty()) {
                    System.out.println("Dict data empty edit");
                    newStrId = h.getDictionaryData().getMaxUsedId() + 1;
                } else {
                    System.out.println("Dict hier empty edit");
                    newStrId = h.getDictionaryData().getMaxUsedId() + 1;
                }

                h.getDictionary().putIdToString(newStrId, newNode);
                h.getDictionary().putStringToId(newNode, newStrId);
            }

            if (h.getDictionaryData().containsString(oldNode)) {
                oldStrId = h.getDictionaryData().getStringToId(oldNode);
            } else {
                oldStrId = h.getDictionary().getStringToId(oldNode);
            }

            h.edit((double) oldStrId, (double) newStrId);
        } else {
            if (h.getHierarchyType().equals("range")) {

                String del = "-";

                if (newNode.contains(" ")) {
                    newNode = newNode.replaceAll(" ", "");
                }

                if (oldNode.contains(" ")) {
                    oldNode = oldNode.replaceAll(" ", "");
                }

                String[] tempNew = newNode.split(del);
                String[] tempOld = oldNode.split(del);

                if (h.getNodesType().equals("date")) {
                    RangeDate newDateNode, oldDateNode;

                    if (tempNew.length == 2)
                        newDateNode = new RangeDate(((HierarchyImplRangesDate) h)
                                .getDateFromString(tempNew[0], true),
                                ((HierarchyImplRangesDate) h).getDateFromString(tempNew[1], false));
                    else
                        newDateNode = new RangeDate(((HierarchyImplRangesDate) h)
                                .getDateFromString(newNode, true),
                                ((HierarchyImplRangesDate) h).getDateFromString(newNode, false));

                    if (tempOld.length == 2)
                        oldDateNode = new RangeDate(((HierarchyImplRangesDate) h)
                                .getDateFromString(tempOld[0], true), ((HierarchyImplRangesDate) h)
                                .getDateFromString(tempOld[1], false));
                    else
                        oldDateNode = new RangeDate(((HierarchyImplRangesDate) h)
                                .getDateFromString(oldNode, true), ((HierarchyImplRangesDate) h)
                                .getDateFromString(oldNode, false));

                    h.edit(oldDateNode, newDateNode);
                } else { // range double
                    RangeDouble newNodeRange = RangeDouble.parseRange(newNode);
                    RangeDouble oldNodeRange = RangeDouble.parseRange(oldNode);
                    newNodeRange.setNodesType(h.getNodesType());
                    oldNodeRange.setNodesType(h.getNodesType());


                    h.edit(oldNodeRange, newNodeRange);
                }
            } else { // distinct
                //// TODO edit check intdouble existance
                h.edit(Double.parseDouble(oldNode), Double.parseDouble(newNode));
            }
        }

        return "ok";
    }

    @PostMapping("/action/deletenodehier")
    public @ResponseBody
    String delNodeHier(@RequestParam("deletenode") String delnode,
                       @RequestParam("hiername") String hierName,
                       HttpSession session) throws ParseException {
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");

        Hierarchy h = hierarchies.get(hierName);

        if (h.getNodesType().equals("string")) {
            if (delnode.equals("(null)")) {
                delnode = "NaN";
            }
            DictionaryString dict;
            if (h.getDictionary().containsString(delnode)) {
                dict = h.getDictionary();
            } else {
                dict = h.getDictionaryData();

            }

            double nodeId = dict.getStringToId(delnode);
            Double root = (Double) h.getRoot();
            if (root == nodeId) {
                return "You can not delete root";
            }
            h.remove(nodeId);
        } else {
            if (h.getHierarchyType().equals("range")) {
                String del = "-";

                if (delnode.contains(" ")) {
                    delnode = delnode.replaceAll(" ", "");
                }

                String[] temp = delnode.split(del);

                if (h.getNodesType().equals("date")) {
                    RangeDate delDate;
                    if (delnode.equals("(null)")) {
                        delDate = new RangeDate(null, null);

                    } else if (temp.length == 2) {
                        delDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(temp[0], true), ((HierarchyImplRangesDate) h).getDateFromString(temp[1], false));
                    } else {
                        delDate = new RangeDate(((HierarchyImplRangesDate) h).getDateFromString(delnode, true), ((HierarchyImplRangesDate) h).getDateFromString(delnode, false));
                    }

                    RangeDate root = (RangeDate) h.getRoot();
                    if (root.equals(delDate)) {
                        return "You can not delete root";
                    }
                    h.remove(delDate);
                } else {
                    RangeDouble delRange = RangeDouble.parseRange(delnode);
                    RangeDouble root = (RangeDouble) h.getRoot();
                    if (root.equals(delRange)) {
                        return "You can not delete root";
                    }
                    h.remove(delRange);
                }
            } else {
                double delValue;
                if (delnode.equals("(null)")) {
                    delValue = Double.NaN;
                    try {
                        h.remove(delValue);
                    } catch (Exception e) {
                        h.remove(2147483646.0);
                    }
                } else {
                    delValue = Double.parseDouble(delnode);
                    Double root = (Double) h.getRoot();
                    if (root.equals(delValue)) {
                        return "You can not delete root";
                    }
                    h.remove(delValue);
                }

            }
        }

        return "OK";

    }

    @PostMapping("/action/findsolutionstatistics")
    public @ResponseBody
    String[] findSolutionStatistics(HttpSession session) throws ParseException {
        Data data = (Data) session.getAttribute("data");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        String selectedNode = (String) session.getAttribute("selectednode");
        System.out.println("Selected Node " + selectedNode);
        Map<Integer, Set<String>> toSuppress = new HashMap<>();
        String[] attr;
        int[] qids = new int[quasiIdentifiers.keySet().size()];
        int i = 0;
        for (Integer column : quasiIdentifiers.keySet()) {
            qids[i] = column;
            i++;
        }

        FindSolutions sol = new FindSolutions(data, quasiIdentifiers, selectedNode, qids, toSuppress);
        Map<SolutionHeader, SolutionStatistics> solMap = sol.getSolutionStatistics();
        session.setAttribute("solutionstatistics", solMap);

        attr = new String[solMap.size()];
        i = 0;
        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
            SolutionStatistics s = entry.getValue();
            s.print();
            attr[i] = entry.getKey().toString();
            i++;
        }

        return attr;

    }

    @JsonView(View.Solutions.class)
    @GetMapping("/action/getsolutionstatistics")
    public @ResponseBody
    SolutionsArrayList getSolutionStatistics(@RequestParam("selectedattributenames") String selectedAttrNames,
                                             HttpSession session) {

        Map<SolutionHeader, SolutionStatistics> solMap;
        Map<SolutionHeader, SolutionStatistics> solMapSuppress = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solmapsuppress");
        SolutionStatistics solStat;
        SolutionsArrayList solutions = null;
        Map<SolutionHeader, Set<String>> nonAnonymousValues = new HashMap<>();
        Map<SolutionHeader, Integer> nonAnonymizedCount = new LinkedHashMap<>();
        boolean suppress = false;
        Data dataset = (Data) session.getAttribute("data");
        StringBuilder selectedAttr = null;
        String[] temp;
        int k = (int) session.getAttribute("k");
        boolean FLAG = false;
       // int[] qids;
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        int dataSizeSuppress = 0;


        if (solMapSuppress == null) {
            solMap = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solutionstatistics");
        } else {
            solMap = solMapSuppress;
        }


        if (selectedAttrNames.contains(" ")) {
            temp = selectedAttrNames.split(" ");
            for (String s : temp) {
                if (!FLAG) {
                    for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
                        if (entry.getKey().toString().equals(s)) {
                            SolutionHeader sol1 = entry.getKey();
                        }
                    }

                    selectedAttr = new StringBuilder("" + dataset.getColumnByName(s));
                    FLAG = true;
                } else {

                    selectedAttr.append(",").append(dataset.getColumnByName(s));
                }
            }
        } else {
            for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
                if (entry.getKey().toString().equals(selectedAttrNames)) {
                    SolutionHeader sol1 = entry.getKey();
                }
            }
            selectedAttr = new StringBuilder("" + dataset.getColumnByName(selectedAttrNames));
           // qids = new int[1];
          //  qids[0] = Integer.parseInt(selectedAttr.toString());
            //quasiIdentifiers.put(Integer.parseInt(selectedAttr), null);
        }


        session.setAttribute("pagenumsolution", 0);
        session.setAttribute("selectedattributenames", selectedAttrNames);
        session.setAttribute("selectedattributes", selectedAttr.toString());
        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
            solStat = entry.getValue();
            solStat.sort();
            Set<String> setAnon = new HashSet<>();
            int count = 0;
            for (SolutionAnonValues values : solStat.getKeyset()) {
                if (selectedAttrNames.equals(entry.getKey().toString())) {
                    dataSizeSuppress += solStat.getSupport(values);
                }
                if (solStat.getSupport(values) < k) {
                    count += solStat.getSupport(values);
                    setAnon.add(values.toString());
                    if (entry.getKey().toString().equals(selectedAttrNames) && checkQuasi(dataset, quasiIdentifiers, selectedAttrNames)) {
                        suppress = true;
                    }
                }
            }
            if (count != 0) {
                nonAnonymizedCount.put(entry.getKey(), count);
                nonAnonymousValues.put(entry.getKey(), setAnon);
                session.setAttribute("nonanonymousvalues", nonAnonymousValues);
                session.setAttribute("nonanonymizedcount", nonAnonymizedCount);
            }
        }

        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {

            if (entry.getKey().toString().equals(selectedAttrNames)) {
                double percentageSuppress = 0.0;
                if (nonAnonymizedCount.get(entry.getKey()) != null) {
                    percentageSuppress = ((double) nonAnonymizedCount.get(entry.getKey()) / dataSizeSuppress) * 100;

                    DecimalFormat df = new DecimalFormat("#.##");
                    percentageSuppress = Double.valueOf((df.format(percentageSuppress)).replaceAll(",", "."));
                }
                solStat = entry.getValue();
                solStat.sort();

                solutions = solStat.getPage(0, 20, k);
                solutions.setPercentangeSuppress(percentageSuppress);
                solutions.setSuppress(suppress);

            }
        }
        return solutions;

    }

    @JsonView(View.Solutions.class)
    @PostMapping("/action/suppress")
    public @ResponseBody
    SolutionsArrayList suppressValues(HttpSession session) throws ParseException {
        Map<SolutionHeader, Set<String>> nonAnonymousValues = (Map<SolutionHeader, Set<String>>) session.getAttribute("nonanonymousvalues");

        Map<Integer, Set<String>> toSuppress = (Map<Integer, Set<String>>) session.getAttribute("tosuppress");
        if (toSuppress == null) {
            toSuppress = new HashMap<>();
        }

        Map<SolutionHeader, SolutionStatistics> solMap = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solutionstatistics");
        SolutionHeader selectedHeader = null;
        int[] qids = null;
        String node;
        Map<Integer, Hierarchy> quasiIdentifiers;
        Data dataset = (Data) session.getAttribute("data");
        SolutionsArrayList solutions = null;

//        System.out.println("SupresssValues//////////////////////////////////////////");
        String selectedAttrNames = (String) session.getAttribute("selectedattributenames");
        int k = (int) session.getAttribute("k");

        quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        if (quasiIdentifiers != null) {
            qids = new int[quasiIdentifiers.keySet().size()];
            int i = 0;
            for (Integer column : quasiIdentifiers.keySet()) {
                qids[i] = column;
                i++;
            }

        }


        node = (String) session.getAttribute("selectednode");
        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
            if (entry.getKey().toString().equals(selectedAttrNames)) {
                selectedHeader = entry.getKey();
            }
        }


        int curQid = -1;

        //if one column is selected, add non-anonymous values of this column
        if (selectedHeader.qids.length == 1) {
            curQid = selectedHeader.qids[0];
            toSuppress.put(curQid, nonAnonymousValues.get(selectedHeader));
        }
        //else add non-anonymous values of all columns to be suppressed
        else {

            for (SolutionHeader header : nonAnonymousValues.keySet()) {

                if (header.qids.length == 1)
                    curQid = header.qids[0];
                else
                    curQid = -1;


                toSuppress.put(curQid, nonAnonymousValues.get(header));
            }
        }


        FindSolutions solution = new FindSolutions(dataset, quasiIdentifiers, node, qids, toSuppress);
        solMap = solution.getSolutionStatistics();
        session.setAttribute("tosuppress", toSuppress);

        session.setAttribute("solmapsuppress", solMap);
        SolutionStatistics solStat;
        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {

            if (entry.getKey().toString().equals(selectedAttrNames)) {
                solStat = entry.getValue();
                solStat.sort();
                solutions = solStat.getPage(0, 50, k);
            }
        }
        return solutions;

    }

    @JsonView(View.Solutions.class)
    @GetMapping("/action/getsourcestatistics")
    public @ResponseBody
    SolutionsArrayList getSourceStatistics(@RequestParam("kcheck") String k,
                                           @RequestParam("selectedattribute") String selectedAttr,
                                           @RequestParam("selectedattributenames") String selectedAttrNames,
                                           HttpSession session) throws ParseException {
        Data dataset = (Data) session.getAttribute("data");
        Map<Integer, Set<String>> toSuppress = new HashMap<>();
        boolean suppress = false;

        String[] temp;
        String del = ",";
        int[] qids;
        StringBuilder node = null;
        boolean FLAG = false;
        Map<Integer, Hierarchy> quasiIdentifiers = new HashMap<>();
        SolutionsArrayList solutionsArr = null;
        Map<SolutionHeader, Set<String>> nonAnonymousValues = new HashMap<>();
        Map<SolutionHeader, Integer> nonAnonymizedCount = new LinkedHashMap<>();
        int dataSizeSuppress = 0;
        if (selectedAttr.contains(",")) {
            temp = selectedAttr.split(del);
            qids = new int[temp.length];
            for (int i = 0; i < temp.length; i++) {
                if (!FLAG) {
                    node = new StringBuilder("0");
                    FLAG = true;
                } else {
                    node.append(",0");
                }
                qids[i] = Integer.parseInt(temp[i]);
                quasiIdentifiers.put(Integer.parseInt(temp[i]), null);
                selectedAttrNames = selectedAttrNames.replaceAll(",", " ");
            }
        } else {
            qids = new int[1];
            qids[0] = Integer.parseInt(selectedAttr);
            node = new StringBuilder("0");
            quasiIdentifiers.put(Integer.parseInt(selectedAttr), null);

        }

        FindSolutions solution = new FindSolutions(dataset, quasiIdentifiers, node.toString(), qids, toSuppress);
        Map<SolutionHeader, SolutionStatistics> solMap = solution.getSolutionStatistics();
        session.setAttribute("pagenumsolution", 0);
        SolutionStatistics solStat;
        session.setAttribute("selectedattributenames", selectedAttrNames);
        session.setAttribute("quasiIdentifiers", quasiIdentifiers);
        session.setAttribute("selectedattributes", selectedAttr);
        int intK = Integer.parseInt(k);
        session.setAttribute("k", intK);

        session.setAttribute("solutionstatistics", solMap);
        session.setAttribute("selectednode", node.toString());


        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
            solStat = entry.getValue();
            solStat.sort();
            Set<String> setAnon = new HashSet<>();
            int count = 0;
            for (SolutionAnonValues values : solStat.getKeyset()) {
                if (selectedAttrNames.equals(entry.getKey().toString())) {
                    dataSizeSuppress += solStat.getSupport(values);
                }

                if (solStat.getSupport(values) < Integer.parseInt(k)) {
                    count += solStat.getSupport(values);
                    setAnon.add(values.toString());
                    suppress = true;
                }
            }
            if (count != 0) {
                nonAnonymizedCount.put(entry.getKey(), count);
                nonAnonymousValues.put(entry.getKey(), setAnon);
                session.setAttribute("nonanonymousvalues", nonAnonymousValues);
                session.setAttribute("nonanonymizedcount", nonAnonymizedCount);

            }
        }


        for (Map.Entry<SolutionHeader, SolutionStatistics> entry : solMap.entrySet()) {
            if (entry.getKey().toString().equals(selectedAttrNames)) {
                double percentageSuppress = 0.0;
                if (nonAnonymizedCount.get(entry.getKey()) != null) {
                    percentageSuppress = ((double) nonAnonymizedCount.get(entry.getKey()) / dataSizeSuppress) * 100;

                    DecimalFormat df = new DecimalFormat("#.##");
                    percentageSuppress = Double.parseDouble((df.format(percentageSuppress)).replaceAll(",", "."));
                }
                solStat = entry.getValue();

                //solStat.getSuppressPercentage();

                solStat.sort();

                solutionsArr = solStat.getPage(0, 20, intK);
                solutionsArr.setPercentangeSuppress(percentageSuppress);
                solutionsArr.setSuppress(suppress);

            }
        }
        return solutionsArr;

    }

    @PostMapping("/action/getdashboard")
    public @ResponseBody
    String getdashboard(HttpSession session) {
        String str;
        Data data = (Data) session.getAttribute("data");
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        String selectedNode = (String) session.getAttribute("selectednode");

        if (data != null) {
            if (hierarchies != null) {
                if (quasiIdentifiers != null) {
                    if (selectedNode != null) {
                        str = "data_hier_algo_solution";
                    } else {
                        str = "data_hier_algo";
                    }
                } else {
                    str = "data_hier";
                }

            } else {
                str = "data";
            }
        } else {
            str = "null";
        }

        return str;
    }

    @PostMapping("/action/checkdataset")
    public @ResponseBody
    int checkDataset(@RequestParam("attributes") String attributes,
                     HttpSession session) {
        Data data = (Data) session.getAttribute("data");
        Set<Integer> sQids = new HashSet<>();
        if (!attributes.equals("")) {
            if (attributes.contains(",")) {
                Arrays.stream(attributes.split(",")).forEach(item -> sQids.add(Integer.parseInt(item)));
            } else {
                sQids.add(Integer.parseInt(attributes));
            }
        }
        CheckDatasetForKAnomymous check = new CheckDatasetForKAnomymous(data);
        return check.compute(sQids);

    }

    @PostMapping("/action/deletesuppress")
    public @ResponseBody
    void deleteSuppress(HttpSession session) {
        if (session.getAttribute("tosuppress") != null) {
            session.removeAttribute("tosuppress");
        }
        if (session.getAttribute("solmapsuppress") != null) {
            session.removeAttribute("solmapsuppress");
        }
        if (session.getAttribute("anondata") != null) {
            session.removeAttribute("anondata");
        }

        if (session.getAttribute("selectednode") != null) {
            session.removeAttribute("selectednode");
        }
    }

    @PostMapping("/action/deletedataset")
    public @ResponseBody
    void deleteDataset(HttpSession session) {
        session.removeAttribute("filename");
        session.removeAttribute("data");
        session.removeAttribute("inputpath");
        session.removeAttribute("anondata");
        session.removeAttribute("selectednode");
    }


    @PostMapping("/action/deletehier")
    public @ResponseBody
    void deleteHier(HttpSession session) {
        session.removeAttribute("hierarchies");
        session.removeAttribute("selectedhier");
    }

    @PostMapping("/action/getcolumnnamesandtypes")
    public @ResponseBody
    ArrayList<ColumnsNamesAndTypes> getColumnNamesAndTypes(HttpSession session) {
        Data data = (Data) session.getAttribute("data");
        ArrayList<ColumnsNamesAndTypes> colTypeList = new ArrayList<>();
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        Hierarchy h;
        ColumnsNamesAndTypes colNamesTypes;
//        System.out.println("dimitris");
        for (Map.Entry<Integer, String> entry : data.getColNamesPosition().entrySet()) {
            String columnName = entry.getValue();
            h = quasiIdentifiers.get(entry.getKey());
            if (h != null) {
                colNamesTypes = new ColumnsNamesAndTypes(columnName, h.getHierarchyType());
            } else {
                colNamesTypes = new ColumnsNamesAndTypes(columnName, "distinct");
            }
            colTypeList.add(colNamesTypes);
        }


        return colTypeList;

    }


    @PostMapping("/action/gethiernamesandlevels")
    public @ResponseBody
    ArrayList<HierarchiesAndLevels> getHierNamesAndlevels(HttpSession session) {
        ArrayList<HierarchiesAndLevels> hiersAndLevels = new ArrayList<>();
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        Hierarchy h;
        HierarchiesAndLevels hiersAndlevels;

        for (Map.Entry<Integer, Hierarchy> entry : quasiIdentifiers.entrySet()) {
            h = entry.getValue();
            hiersAndlevels = new HierarchiesAndLevels(h.getName(), h.getHeight() + "");
            hiersAndLevels.add(hiersAndlevels);
        }
        return hiersAndLevels;

    }

    @GetMapping("/action/createdatabase")
    public @ResponseBody
    String createDatabase(@RequestParam("name") String name) throws
            ClassNotFoundException,
            NoSuchMethodException,
            InvocationTargetException,
            InstantiationException,
            IllegalAccessException {
        Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
        String url = "jdbc:sqlite:" + rootPath + File.separator + name;
        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("The driver name is " + meta.getDriverName());
                System.out.println("A new database has been created.");
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return e.getMessage();
        }
        return "Database created";
    }

    @GetMapping("/action/checkanonymity")
    public @ResponseBody
    String checkAnonymity(@RequestParam("k") Integer k,
                          @RequestParam("m") Integer m,
                          @RequestParam(value = "cols", required = false) String columns,
                          HttpSession session) {
        Data data = (Data) session.getAttribute("data");
        MixedApriori alg = new MixedApriori();
        alg.setDictionary(data.getDictionary());
        if (!(columns == null || columns.equals(""))) {
            String[] strcols = columns.split(",");
            List<Integer> columnsCheck = new ArrayList<Integer>();
            for (String col : strcols) {
                columnsCheck.add(Integer.parseInt(col));
            }


            alg.setDataTable(data.getDataSet());
            if (data instanceof RelSetData) {
                alg.setSetData(((RelSetData) data).getSet());
                return alg.checkAnonymity(k, m, columnsCheck, ((RelSetData) data).getSetColumn());
            } else {
                return alg.checkAnonymity(k, m, columnsCheck, -1);
            }
        } else {
            alg.setSetData(data.getDataSet());
            return alg.checkAnonymitySet(k, m);
        }
    }

    @PostMapping("/action/getresultsstatisticsqueries")
    public @ResponseBody
    double[] getResultsStatisticsQueries(@RequestParam("identifiers[]") String[] identifiersArray,
                                         @RequestParam("min[]") String[] minArray,
                                         @RequestParam("max[]") String[] maxArray,
                                         @RequestParam("distinct[]") String[] distinctArr,
                                         HttpSession session) {
        Data data = (Data) session.getAttribute("data");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        String[] identifiersArr = new String[identifiersArray.length];
        double[] minArr = new double[identifiersArray.length];
        double[] maxArr = new double[identifiersArray.length];
        AnonymizedDataset anonData = (AnonymizedDataset) session.getAttribute("anondata");
        double[] resultArr;

        for (int i = 0; i < identifiersArray.length; i++) {
            if (identifiersArray[i].equals("null")) {
                identifiersArr[i] = null;
            } else {
                identifiersArr[i] = identifiersArray[i];
            }
        }


        for (int i = 0; i < minArray.length; i++) {
            if (minArray[i].equals("null")) {
                minArr[i] = Double.NaN;
            } else {
                minArr[i] = Double.parseDouble(minArray[i]);
            }
        }

//        System.out.println("MaxArr");
        for (int i = 0; i < maxArray.length; i++) {
//            System.out.println(maxArray[i]);
            if (maxArray[i].equals("null")) {
                maxArr[i] = Double.NaN;
            } else {
                maxArr[i] = Double.parseDouble(maxArray[i]);
            }
        }


//        System.out.println("distinct");
        for (int i = 0; i < distinctArr.length; i++) {
            if (distinctArr[i].equals("null")) {
                distinctArr[i] = null;
            }
//            System.out.println(distinctArr[i]);
        }

//        System.out.println("anon" );

        Queries queries = new Queries(identifiersArr, minArr, maxArr, distinctArr, hierarchies, data, anonData.getHierarchyLevel(), quasiIdentifiers);
        Results results = queries.executeQueries();

        resultArr = new double[4];
        resultArr[0] = Double.parseDouble(results.getNonAnonymizeOccurrences());
        resultArr[1] = Double.parseDouble(results.getAnonymizedOccurrences());
        resultArr[2] = Double.parseDouble(results.getPossibleOccurences());
        try {
            resultArr[3] = Double.parseDouble(results.getEstimatedRate());
        } catch (Exception e) {
            resultArr[3] = Double.parseDouble(results.getEstimatedRate().split(",")[0]);
        }
        return resultArr;

    }


    @PostMapping("/action/getproperalgorithm")
    public @ResponseBody
    String getProperAlgorithm(HttpSession session) {
        Data data = (Data) session.getAttribute("data");

        if (data.getClass().toString().contains("SET")) {
            return "set";
        } else if (data instanceof RelSetData) {
            return "relset";
        } else if (data instanceof DiskData) {
            return "disk";
        } else {
            return "txt";
        }

    }

    @PostMapping("/action/getsetvaluedcolumn")
    public @ResponseBody
    String getSetValuedColumn(HttpSession session) {
        Data data = (Data) session.getAttribute("data");

        if (data instanceof RelSetData) {
            RelSetData relsetdata = (RelSetData) data;
            return data.getColNamesPosition().get(relsetdata.getSetColumn()) + "_" + relsetdata.getSetColumn();
        } else {
            return "none";
        }

    }


    @PostMapping("/action/savenonymizationrules")
    public @ResponseBody
    String saveAnonynizationRules(HttpSession session, HttpServletResponse response) throws IOException, ParseException {
        Data data = (Data) session.getAttribute("data");
        Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
        String selectedNode;
        String filename = (String) session.getAttribute("filename");
        String inputPath = (String) session.getAttribute("inputpath");
//        System.out.println("Filename :"+filename+" "+filename.endsWith("xml") +" "+(filename.endsWith("xml") ? filename.replace("xml", "txt") : filename));
        String file = inputPath + File.separator + "anonymized_rules_" + (filename.endsWith("xml") ? filename.replace("xml", "txt") : filename);

        this.createInputPath(inputPath);

        AnonymizationRules anonRules = new AnonymizationRules();
        if (data instanceof SETData) {
            Map<Double, Double> results = (Map<Double, Double>) session.getAttribute("results");
            anonRules.export(file, data, results, quasiIdentifiers);
        } else if (data instanceof RelSetData) {
            Map<Integer, Map<Object, Object>> results = (Map<Integer, Map<Object, Object>>) session.getAttribute("results");
            anonRules.exportRelSet(file, data, results, quasiIdentifiers);
        } else {
            int[] qids = new int[quasiIdentifiers.keySet().size()];
            int i = 0;
            for (Integer column : quasiIdentifiers.keySet()) {
                qids[i] = column;
                i++;
            }

            selectedNode = (String) session.getAttribute("selectednode");
            Map<Integer, Set<String>> toSuppress = (Map<Integer, Set<String>>) session.getAttribute("tosuppress");
            anonRules.export(file, data, qids, toSuppress, quasiIdentifiers, selectedNode);
        }
        File anonFile = new File(file);
//        if(!anonFile.exists()){
//            anonFile.mkdirs();
        anonFile.createNewFile();
//        }

        InputStream myStream = new FileInputStream(file);

        // Set the content type and attachment header.
        response.addHeader("Content-disposition", "attachment;filename=" + anonFile.getName());
        response.setContentType("txt/plain");

        // Copy the stream to the response's output stream.
        IOUtils.copy(myStream, response.getOutputStream());
        response.flushBuffer();

        if (os.equals("online")) {
            this.deleteFiles(session);
        }

        return null;
    }


    @PostMapping("/action/loadanonymizationrules")
    public @ResponseBody
    String loadAnonynizationRules(HttpSession session, String filename){
        try {
            String inputPath = (String) session.getAttribute("inputpath");
            this.createInputPath(inputPath);
            //String anonRulesFile = inputPath +"/anonymized_rules_"+filename;
            String anonRulesFile = inputPath + File.separator + filename;
            AnonymizationRules anonRules = new AnonymizationRules();
            if (!anonRules.importRules(anonRulesFile)) {
                return "File structure not supported for anonymization rules!";
            }
            Map<String, Map<String, String>> rules = anonRules.getAnonymizedRules();
            session.setAttribute("anonrules", rules);

            if (os.equals("online")) {
                this.deleteFiles(session);
            }
            return null;
        } catch (Exception e) {
            return "Problem with loading anonymization rules " + e.getMessage();
        }
    }

    //@JsonView(View.DatasetsExists.class)
    @PostMapping("/action/checkdatasetsexistence")
    public @ResponseBody
    DatasetsExistence CheckDatasetsExistence(HttpSession session) {

        DatasetsExistence check = new DatasetsExistence();
        Data data = (Data) session.getAttribute("data");
        String selectednode = (String) session.getAttribute("selectednode");
        Map<String, Map<String, String>> allRules = (Map<String, Map<String, String>>) session.getAttribute("anonrules");
        Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");


        String algorithm = (String) session.getAttribute("algorithm");
        check.setAlgorithm(algorithm);
        check.setDiskData(data);
        if (data != null && allRules != null) {
            check.setOriginalExists("true");
            check.setAnonExists("true");
        } else if (data != null && algorithm == null) {
            check.setOriginalExists("true");
            check.setAnonExists("noalgo");
        } else if (data != null && hierarchies != null && algorithm != null && (algorithm.equals("kmAnonymity") || algorithm.equals("apriori") || algorithm.equals("AprioriShort") || algorithm.equals("clustering")) && selectednode == null) {
            check.setOriginalExists("true");
            check.setAnonExists("true");
        } else if (data != null && algorithm != null && selectednode == null) {
            check.setOriginalExists("true");
        } else if (data != null && algorithm != null && selectednode != null) {
            check.setOriginalExists("true");
            check.setAnonExists("true");
        }
        return check;
    }
    @PostMapping("/anonymizedata")
    @Produces(MediaType.TEXT_PLAIN)
    public void AnonimizeData(@RequestParam("files") MultipartFile[] files,
                              @RequestParam("del") String del,
                              HttpSession session,
                              HttpServletResponse response) throws Exception {
        try {
            this.upload(files[0], true, session);
            String path = (String) session.getAttribute("inputpath");
            String filename = (String) session.getAttribute("filename");
            File templateFile;
            if (del.equals("s")) {
                del = ";";
            }
            if (files.length == 1 || files[1] == null) {


                Data dataset = this.getSmallDataSet(del, "tabular", "", session);
                String[][] types = dataset.getTypesOfVariables(dataset.getSmallDataSet());
                templateFile = new File(path + File.separator + "template.txt");
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(templateFile.getAbsolutePath(), true), StandardCharsets.UTF_8));

                File dataFile = new File(path + File.separator + filename);
                FileInputStream fileStream = new FileInputStream(dataFile);
                DataInputStream inData = new DataInputStream(fileStream);
                BufferedReader br = new BufferedReader(new InputStreamReader(inData, StandardCharsets.UTF_8));

                if (!filename.endsWith(".xml")) {
                    String firstLine = br.readLine();
                    br.close();

                    String[] splitLine = firstLine.split(del);
                    out.write("////////////////////// check columns, vartypes /////////////////////////////");
                    out.newLine();
                    for (int i = 0; i < splitLine.length; i++) {
                        out.write(splitLine[i] + ": true," + types[i][0].replace("double", "decimal"));
                        out.newLine();

                    }
                    out.write("//////////////////// END ////////////////////////////////////////////\n\n");
                    out.write("\n" +
                            "/////////////////// set k /////////////////////////////////////\n\n" +
                            "k:\n");
                    out.close();
                } else {
                    XMLData xmldata = (XMLData) dataset;
                    String[] names = xmldata.getColumnNames();
                    out.write("////////////////////// check columns, vartypes /////////////////////////////");
                    out.newLine();
                    for (int i = 0; i < names.length; i++) {
                        out.write(names[i] + ": true," + types[i][0].replace("double", "decimal"));
                        out.newLine();
                    }
                    out.write("//////////////////// END ////////////////////////////////////////////\n\n");
                    out.write("\n" +
                            "/////////////////// set k /////////////////////////////////////\n\n" +
                            "k:\n");
                    out.close();
                }

                InputStream in = new FileInputStream(templateFile);
                FileCopyUtils.copy(in, response.getOutputStream());


            } else {


                for (int i = 2; i < files.length; i++) {
                    this.hierarchy(files[i], false, session);
                }

                this.upload(files[1], false, session);
                this.upload(files[0], false, session);
                File templ = new File(path + File.separator + files[1].getOriginalFilename());
                FileInputStream fileStream = new FileInputStream(templ);
                DataInputStream inData = new DataInputStream(fileStream);
                BufferedReader br = new BufferedReader(new InputStreamReader(inData, StandardCharsets.UTF_8));

                String strline;
                List<String> vartypesArr, relationsArr;
                List<Boolean> checkColumnsArr;
                int k = 0;

                vartypesArr = new ArrayList<>();
                relationsArr = new ArrayList<>();
                checkColumnsArr = new ArrayList<>();

                ArrayList<String> possibleTypes = new ArrayList() {{
                    add("int");
                    add("decimal");
                    add("date");
                    add("string");
                }};
                String error_msg = "";

                while ((strline = br.readLine()) != null) {
                    if (strline.contains("END") || strline.length() <= 1) {
                        continue;
                    } else if (strline.contains("check columns, vartypes")) {
                        while (!(strline = br.readLine()).contains("END")) {
                            String[] columnInfo = strline.split(":");
                            String[] attributes = columnInfo[1].replaceAll("\n", "").replaceAll(" ", "").split(",");

//                       System.out.println("length attr: "+attributes.length);
                            if (attributes.length > 1 && attributes.length <= 3) {
                                if (!attributes[0].equals("true") && !attributes[0].equals("false")) {
                                    error_msg += "In " + columnInfo[0] + ": bollean type must be true or false.\n";
                                }
                                checkColumnsArr.add(attributes[0].equals("true"));

                                if (!possibleTypes.contains(attributes[1])) {
                                    error_msg += "In " + columnInfo[0] + ": not accepted variable type. It must be one of the " + Arrays.toString(possibleTypes.toArray()) + "\n";
                                }
                                vartypesArr.add(attributes[1].replace("decimal", "double"));

                                if (attributes.length == 3) {
                                    relationsArr.add(attributes[2]);
                                } else {
                                    relationsArr.add("");
                                }
                            } else {
                                error_msg += "In " + columnInfo[0] + ":  missing boolean type or the variable type of the column.\n";
                            }
                        }
                    } else if (strline.contains("set k")) {
                        while (!(strline = br.readLine()).contains("k:")) ;
                        String[] splits = strline.split(":");
                        try {
                            k = Integer.parseInt(splits[1].replaceAll(" ", ""));
                        } catch (NumberFormatException | NullPointerException nfe) {
                            error_msg += "k is not set or its not a number.\n";
//                       response.getOutputStream().println("k is not set or its not a number.");
//                       return;
                        }
                    }


                }

                if (!error_msg.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getOutputStream().println(error_msg);
                    return;
                }

//            System.out.println("Results: k->"+k+" vartypes-> "+Arrays.toString(vartypesArr.toArray(new String[vartypesArr.size()]))+" checkColumns-> "+Arrays.toString(checkColumnsArr.toArray())+" relations-> "+Arrays.toString(relationsArr.toArray()));
                this.getSmallDataSet(del, "tabular", "", session);
                boolean[] checkColumns = new boolean[checkColumnsArr.size()];
                for (int i = 0; i < checkColumns.length; i++) {
                    checkColumns[i] = checkColumnsArr.get(i);
                }
                this.loadDataset(vartypesArr.toArray(new String[vartypesArr.size()]), checkColumns, session);


                this.anonymize(k, 0, "pFlash", relationsArr.toArray(new String[relationsArr.size()]), session);
                String solution = this.InformationLoss(session);
                this.setSelectedNode(session, solution);
                this.getAnonDataSet(0, 0, session);
                this.saveAnonymizeDataset(session, response);

            }

            response.flushBuffer();
            if (os.equals("online")) {
                this.deleteFiles(session);
            }

        } catch (Exception e) {
            e.printStackTrace();
            this.errorHandling(e.getLocalizedMessage(), session);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getOutputStream().println("Failed anonymization procedure!");
        }

    }

    @PostMapping("/getSession")
    public void getSession(HttpSession session, HttpServletResponse response) throws IOException {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Success");
            jsonAnswer.put("Session_Id", session.getId());
            response.getOutputStream().print(jsonAnswer.toString());
        } catch (Exception e) {
            e.printStackTrace();
            this.errorHandling(e.getLocalizedMessage(), session);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed to send session id, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/loadData")
    public void loadData(@RequestParam("file") MultipartFile file,
                         @RequestParam("datasetType") String datasetType,
                         @RequestParam("del") String del,
                         @RequestParam(value = "columnsType") String dataTypes,
                         @RequestParam(value = "delSet", required = false) String delset,
                         HttpSession session,
                         HttpServletResponse response) throws IOException {

        try {
            this.upload(file, true, session);
            this.getSmallDataSet(del, datasetType, delset, session);
            Data data = (Data) session.getAttribute("data");
            System.out.println("length " + data.getSmallDataSet()[0].length);
            boolean[] checkColumns = new boolean[data.getSmallDataSet()[0].length];
            String[] vartypes = new String[data.getSmallDataSet()[0].length];
            Map<String, String> datatypesMap = jsonToMap(dataTypes);
            for (int i = 0; i < checkColumns.length; i++) {
                if (datatypesMap.containsKey(data.getColumnNames()[i])) {
                    checkColumns[i] = true;
                    String type = datatypesMap.get(data.getColumnNames()[i]);
                    if (type.equals("int") || type.equals("double") || type.equals("decimal") || type.equals("set") || type.equals("string") || type.equals("date")) {
                        vartypes[i] = datatypesMap.get(data.getColumnNames()[i]).replace("decimal", "double");
                    } else {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        JSONObject jsonAnswer = new JSONObject();
                        jsonAnswer.put("Status", "Fail");
                        jsonAnswer.put("Message", "Unsupported data type " + type);
                        response.getOutputStream().print(jsonAnswer.toString());
                        return;
                    }
                } else {
                    checkColumns[i] = false;
                    vartypes[i] = null;
                }

            }
            System.out.println("chackColumns " + Arrays.toString(checkColumns));
            this.loadDataset(vartypes, checkColumns, session);
        } catch (Exception e) {
            e.printStackTrace();
            this.errorHandling(e.getLocalizedMessage(), session);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed load dataset, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        JSONObject jsonAnswer = new JSONObject();
        jsonAnswer.put("Status", "Success");
        jsonAnswer.put("Message", "Dataset loaded successfully!");
        response.getOutputStream().print(jsonAnswer.toString());
    }

    @PostMapping("/generateHierarchy")
    public void generateHierarchy(@RequestParam("hierType") String hierType,
                                  @RequestParam("varType") String varType,
                                  @RequestParam("attribute") String colName,
                                  @RequestParam("hierName") String name,
                                  @RequestParam(value = "startLimit", required = false, defaultValue = "0") int startLimit,
                                  @RequestParam(value = "endLimit", required = false, defaultValue = "0") int endLimit,
                                  @RequestParam(value = "startYear", required = false, defaultValue = "0") int startYear,
                                  @RequestParam(value = "endYear", required = false, defaultValue = "0") int endYear,
                                  @RequestParam(value = "fanout", required = false, defaultValue = "0") int fanout,
                                  @RequestParam(value = "step", required = false, defaultValue = "0") int step,
                                  @RequestParam(value = "years", required = false, defaultValue = "0") int years,
                                  @RequestParam(value = "months", required = false, defaultValue = "0") int months,
                                  @RequestParam(value = "days", required = false, defaultValue = "0") int days,
                                  @RequestParam(value = "sorting", required = false, defaultValue = "0") String sorting,
                                  @RequestParam(value = "length", required = false, defaultValue = "0") int length,
                                  HttpSession session, HttpServletResponse response) throws IOException {

        try {
            Data data = (Data) session.getAttribute("data");
            String limits = "";
            if (hierType.equals("range") && varType.equals("date")) {
                limits += startYear + "-" + endYear;
            } else if (hierType.equals("range")) {
                limits += startLimit + "-" + endLimit;
            }
            int attributeCol = data.getColumnByName(colName);
            this.autogeneratehierarchy(hierType, varType, attributeCol, step, sorting, name, fanout, limits, months, days, years, length, session);
            response.setStatus(HttpServletResponse.SC_OK);
            this.saveHierarchy(response, session);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed to autogenerate an hierarchy, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }

    }

    @PostMapping("/loadHierarchies")
    public void loadHierarchies(@RequestParam("hierarchies") MultipartFile[] hierarchies,
                                HttpSession session,
                                HttpServletResponse response) throws IOException {
        try {
            for (MultipartFile hierarchy : hierarchies) {
                this.hierarchy(hierarchy, false, session);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Success");
            jsonAnswer.put("Message", "Hierarchies have been successfully loaded!");
            response.getOutputStream().print(jsonAnswer.toString());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed to load hierarchies, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/anonymization")
    public void anonymization(@RequestParam("bind") String bind,
                              @RequestParam("k") int k,
                              @RequestParam(value = "m", required = false, defaultValue = "-1") int m,
                              HttpSession session, HttpServletResponse response) throws IOException {

        System.out.println("session anonymization " + session.getId());
        try {
            Data data = (Data) session.getAttribute("data");
            Map<String, Hierarchy> hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
            Map<String, String> colNamesHier = this.jsonToMap(bind);
            Map<Integer, Hierarchy> quasiIdentifiers = new HashMap();

            for (Map.Entry<String, String> colHier : colNamesHier.entrySet()) {
                quasiIdentifiers.put(data.getColumnByName(colHier.getKey()), hierarchies.get(colHier.getValue()));
                System.out.println("colName " + data.getColumnByName(colHier.getKey()) + " Hier " + hierarchies.get(colHier.getValue()));
            }

            String checkHier = null;
            for (Map.Entry<Integer, Hierarchy> entry : quasiIdentifiers.entrySet()) {
                Hierarchy h = entry.getValue();
                checkHier = h.checkHier(data, entry.getKey());
                if (checkHier != null && !checkHier.endsWith("Ok")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", checkHier);
                    response.getOutputStream().print(jsonAnswer.toString());
                    return;
                }
            }

            session.setAttribute("quasiIdentifiers", quasiIdentifiers);
            session.setAttribute("k", k);

            Map<String, Integer> args = new HashMap<>();
            Algorithm algorithm = null;
            if (data instanceof TXTData) {
                args.put("k", k);
                algorithm = new Flash();
                session.setAttribute("algorithm", "flash");
            } else if (data instanceof SETData) {
                args.put("k", k);
                if (m < 0) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "m parameter is required for km-anonymity, please try again!");
                    response.getOutputStream().print(jsonAnswer.toString());
                    return;
                }
                args.put("m", m);
                algorithm = new Apriori();
                session.setAttribute("algorithm", "apriori");
            } else if (data instanceof RelSetData) {
                args.put("k", k);
                if (m < 0) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "m parameter is required for km-anonymity, please try again!");
                    response.getOutputStream().print(jsonAnswer.toString());
                    return;
                }
                args.put("m", m);
                algorithm = new MixedApriori();
                session.setAttribute("algorithm", "apriori");
            } else if (data instanceof DiskData) {
                args.put("k", k);
                algorithm = new ClusterBasedAlgorithm();
                session.setAttribute("algorithm", "clustering");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", "Wrong dataset type, please try again!");
                response.getOutputStream().print(jsonAnswer.toString());
            }

            algorithm.setDataset(data);
            algorithm.setHierarchies(quasiIdentifiers);
            algorithm.setArguments(args);


            final String message = "memory problem";
            String resultAlgo = "";
            Future<String> future = null;
            System.out.println("Algorithm starts");
            try {
                if (os.equals("online")) {
                    ExecutorService executor = Executors.newCachedThreadPool();
                    final Algorithm temp = algorithm;
                    future = executor.submit(() -> {
                        try {
                            temp.anonymize();
                        } catch (OutOfMemoryError e) {
                            e.printStackTrace();
                            return message;
                        }


                        return "Ok";
                    });
                    resultAlgo = future.get(3, TimeUnit.MINUTES);
                } else {
                    algorithm.anonymize();
                }

            } catch (TimeoutException e) {
                // Too long time
                e.printStackTrace();
                future.cancel(true);
                restart(session);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", "Failed to anonymize the dataset, online version is out of time please try again!");
                response.getOutputStream().print(jsonAnswer.toString());
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", message);
                response.getOutputStream().print(jsonAnswer.toString());
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
                Logger.getLogger(AppCon.class.getName()).log(Level.SEVERE, null, ex);
                restart(session);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", "Failed to anonymize the dataset, please try again!");
                response.getOutputStream().print(jsonAnswer.toString());
            }


            if (resultAlgo.equals(message)) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", message);
                response.getOutputStream().print(jsonAnswer.toString());
            }

            if (algorithm.getResultSet() == null) {
                if (!(data instanceof DiskData)) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "Anonymization procedure has no results");
                    response.getOutputStream().print(jsonAnswer.toString());
                }
                this.getAnonDataSet(0, 10, session);
                response.setStatus(HttpServletResponse.SC_OK);
                this.saveAnonymizeDataset(session, response);
            } else {

                session.setAttribute("results", algorithm.getResultSet());
                if (data instanceof TXTData) {
                    Graph graph = algorithm.getLattice();
                    session.setAttribute("graph", graph);
                    JSONObject jsonAnswer = new JSONObject();
                    ArrayList<Node> nodesSol = graph.getNodeList();
                    String idSol = "sol";
                    for (int i = 0; i < nodesSol.size(); i++) {
                        JSONObject levelRes = new JSONObject();
                        levelRes.put("levels", nodesSol.get(i).getLabel());
                        levelRes.put("result", nodesSol.get(i).getColor().toLowerCase().contains("red") ? "unsafe" : "safe");
                        jsonAnswer.put(idSol + i, levelRes);
                    }

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject solutions = new JSONObject();
                    solutions.put("Solutions", jsonAnswer);
                    response.getOutputStream().print(solutions.toString());
                } else {
                    this.getAnonDataSet(0, 0, session);
                    response.setStatus(HttpServletResponse.SC_OK);
                    this.saveAnonymizeDataset(session, response);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed to anonymize the dataset, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/getSolution")
    public void getSolution(@RequestParam("sol") String sol, HttpSession session, HttpServletResponse response) throws IOException {
        try {
            this.deleteSuppress(session);
            sol = sol.trim().replace("[", "").replace("]", "");
            this.setSelectedNode(session, sol);
            this.getAnonDataSet(0, 0, session);
            response.setStatus(HttpServletResponse.SC_OK);
            this.saveAnonymizeDataset(session, response);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Failed to return solution file, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/getSuppressPercentage")
    public void getSuppressPercentage(@RequestParam("sol") String sol, HttpSession session, HttpServletResponse response) throws IOException {

        try {
            this.deleteSuppress(session);
            sol = sol.trim().replace("[", "").replace("]", "");
            this.setSelectedNode(session, sol);
            Data data = (Data) session.getAttribute("data");
            int k = (int) session.getAttribute("k");
            Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
            StringBuilder attributes = new StringBuilder();
            for (Map.Entry<Integer, Hierarchy> quasi : quasiIdentifiers.entrySet()) {
                attributes.append(data.getColumnByPosition(quasi.getKey())).append(" ");
            }
            attributes = new StringBuilder(attributes.substring(0, attributes.length() - 1));
            Map<SolutionHeader, SolutionStatistics> solMap = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solutionstatistics");
            this.findSolutionStatistics(session);
            SolutionsArrayList stats = this.getSolutionStatistics(attributes.toString(), session);
            response.setStatus(HttpServletResponse.SC_OK);
            double suppress = stats.getPercentangeSuppress();
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Success");
            jsonAnswer.put("percentageSuppress", suppress);
            jsonAnswer.put("k", k);
            if (suppress != 0.0) {
                jsonAnswer.put("Message", "To produce a k=" + k + " anonymity solution, it must be suppressed by " + suppress + "%");
            } else {
                jsonAnswer.put("Message", "The solution: [" + sol + "] statisfies k=" + k + " anonymity");
            }
            response.getOutputStream().print(jsonAnswer.toString());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to return the percentage of suppression, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }


    }

    @PostMapping("/getSuppressedSolution")
    public void suppressSolution(@RequestParam("sol") String sol, HttpSession session, HttpServletResponse response) throws IOException {
        try {
            this.deleteSuppress(session);
            String originalSol = sol;
            sol = sol.trim().replace("[", "").replace("]", "");
            this.setSelectedNode(session, sol);
            Data data = (Data) session.getAttribute("data");
            int k = (int) session.getAttribute("k");
            Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
            StringBuilder attributes = new StringBuilder();
            for (Map.Entry<Integer, Hierarchy> quasi : quasiIdentifiers.entrySet()) {
                attributes.append(data.getColumnByPosition(quasi.getKey())).append(" ");
            }
            attributes = new StringBuilder(attributes.substring(0, attributes.length() - 1));
            this.findSolutionStatistics(session);
            SolutionsArrayList stats = this.getSolutionStatistics(attributes.toString(), session);
            response.setStatus(HttpServletResponse.SC_OK);
            double suppress = stats.getPercentangeSuppress();

            if (suppress != 0.0) {
                this.suppressValues(session);
                this.getAnonDataSet(0, 0, session);
                this.saveAnonymizeDataset(session, response);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", "The solution " + originalSol + " satisfies " + k + "-anonymity so it can not be suppressed!");
                response.getOutputStream().print(jsonAnswer.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to suppress solution, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/getStatistics")
    public void getStatistics(@RequestParam("sol") String sol,
                              @RequestParam("quasi_ids") String[] columns,
                              @RequestParam(value = "suppressed", required = false, defaultValue = "false")
                                      boolean suppressed, HttpSession session, HttpServletResponse response) throws IOException {
        try {
            String[] sortedCols;
            Data data = (Data) session.getAttribute("data");
            Set<String> setCols = null;
            if (columns.length > 1) {
                sortedCols = new String[columns.length];

                setCols = new HashSet();
                for (String col : columns) {
                    setCols.add(col.trim());
                }
                String[] colNames = data.getColumnNames();
                int i = 0;
                for (String dcol : colNames) {
                    if (setCols.contains(dcol)) {
                        sortedCols[i] = dcol;
                        i++;
                    }
                }
            } else {
                sortedCols = columns;
            }

            if (suppressed) {
                this.deleteSuppress(session);
                String originalSol = sol;
                sol = sol.trim().replace("[", "").replace("]", "");
                this.setSelectedNode(session, sol);
                int k = (int) session.getAttribute("k");
                Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
                StringBuilder attributes = new StringBuilder();
                if (setCols == null) {
                    setCols = new HashSet(Arrays.asList(sortedCols));
                }
                if (setCols.size() != quasiIdentifiers.entrySet().size()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "\"quasi_ids\" attribute does not contain all quisi identifiers!");
                    response.getOutputStream().print(jsonAnswer.toString());
                    return;
                }
                for (Map.Entry<Integer, Hierarchy> quasi : quasiIdentifiers.entrySet()) {
                    attributes.append(data.getColumnByPosition(quasi.getKey())).append(" ");
                    if (!setCols.contains(data.getColumnByPosition(quasi.getKey()))) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JSONObject jsonAnswer = new JSONObject();
                        jsonAnswer.put("Status", "Fail");
                        jsonAnswer.put("Message", "The quisi identifier: " + data.getColumnByPosition(quasi.getKey()) + " is not provided in \"quasi_ids\"");
                        response.getOutputStream().print(jsonAnswer.toString());
                        return;
                    }
                }
                attributes = new StringBuilder(attributes.substring(0, attributes.length() - 1));
                Map<SolutionHeader, SolutionStatistics> solMap = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solutionstatistics");
                this.findSolutionStatistics(session);
                SolutionsArrayList stats = this.getSolutionStatistics(attributes.toString(), session);
                response.setStatus(HttpServletResponse.SC_OK);
                double suppress = stats.getPercentangeSuppress();
                if (suppress != 0.0) {
                    SolutionsArrayList stats_suppressed = this.suppressValues(session);
                    JSONObject jsonAnswer = new JSONObject();
                    JSONArray jsonarray = new JSONArray();
                    jsonAnswer.put("Status", "Success");
                    int totalRecs = 0;
                    for (Solutions msol : stats_suppressed.getSolutions()) {
                        JSONObject jsonSol = new JSONObject();
                        jsonSol.put("value", msol.getLabel());
                        jsonSol.put("numberOfValues", msol.getData());
                        totalRecs += Integer.parseInt(msol.getData());
                        jsonarray.add(jsonSol);
                    }
                    jsonAnswer.put("AnonymizedStats", jsonarray);
                    jsonAnswer.put("TotalRecords", totalRecs);
                    jsonAnswer.put("k", k);
                    response.getOutputStream().print(jsonAnswer.toString());
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "The solution " + originalSol + " satisfies " + k + "-anonymity so it can not be suppressed!");
                    response.getOutputStream().print(jsonAnswer.toString());
                }
            } else {
                this.deleteSuppress(session);
                sol = sol.trim().replace("[", "").replace("]", "");
                System.out.println("sol " + sol);
                this.setSelectedNode(session, sol);
                int k = (int) session.getAttribute("k");
                Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
                String attributes = "";
                for (String col : sortedCols) {
                    if (quasiIdentifiers.containsKey(data.getColumnByName(col.trim()))) {
                        attributes += col.trim() + " ";
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JSONObject jsonAnswer = new JSONObject();
                        jsonAnswer.put("Status", "Fail");
                        jsonAnswer.put("Message", "The column: " + col.trim() + " is not a quisi identifier!");
                        response.getOutputStream().print(jsonAnswer.toString());
                        return;
                    }
                }
                attributes = attributes.substring(0, attributes.length() - 1);
                this.findSolutionStatistics(session);
                System.out.println("Attributes " + attributes);
                SolutionsArrayList stats = this.getSolutionStatistics(attributes, session);
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject jsonAnswer = new JSONObject();
                JSONArray jsonarray = new JSONArray();
                jsonAnswer.put("Status", "Success");
                int totalRecs = 0;
                for (Solutions msol : stats.getSolutions()) {
                    JSONObject jsonSol = new JSONObject();
                    jsonSol.put("value", msol.getLabel());
                    jsonSol.put("numberOfValues", msol.getData());
                    totalRecs += Integer.parseInt(msol.getData());
                    jsonarray.add(jsonSol);
                }
                jsonAnswer.put("AnonymizedStats", jsonarray);
                jsonAnswer.put("TotalRecords", totalRecs);
                jsonAnswer.put("k", k);
                response.getOutputStream().print(jsonAnswer.toString());
            }


        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to return statistics, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/getAnonRules")
    public void getAnonRules(@RequestParam(value = "sol", required = false) String sol,
                             @RequestParam(value = "suppressed", required = false, defaultValue = "false")
                             boolean suppressed,
                             HttpSession session,
                             HttpServletResponse response) throws IOException {

        try {
            Data data = (Data) session.getAttribute("data");
            if (data instanceof DiskData) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", "Anonymization rules are not available for Disk based clustering algorithm!");
                response.getOutputStream().print(jsonAnswer.toString());
            }

            if (suppressed) {
                if (!(data instanceof TXTData)) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "Suppression is available only for simple table data!");
                    response.getOutputStream().print(jsonAnswer.toString());
                } else if (sol == null) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "Need to provide specific solution");
                    response.getOutputStream().print(jsonAnswer.toString());
                } else {
                    this.deleteSuppress(session);
                    String originalSol = sol;
                    sol = sol.trim().replace("[", "").replace("]", "");
                    this.setSelectedNode(session, sol);
                    int k = (int) session.getAttribute("k");
                    Map<Integer, Hierarchy> quasiIdentifiers = (Map<Integer, Hierarchy>) session.getAttribute("quasiIdentifiers");
                    StringBuilder attributes = new StringBuilder();
                    for (Map.Entry<Integer, Hierarchy> quasi : quasiIdentifiers.entrySet()) {
                        attributes.append(data.getColumnByPosition(quasi.getKey())).append(" ");
                    }
                    attributes = new StringBuilder(attributes.substring(0, attributes.length() - 1));
                    Map<SolutionHeader, SolutionStatistics> solMap = (Map<SolutionHeader, SolutionStatistics>) session.getAttribute("solutionstatistics");
                    this.findSolutionStatistics(session);
                    SolutionsArrayList stats = this.getSolutionStatistics(attributes.toString(), session);
                    response.setStatus(HttpServletResponse.SC_OK);
                    double suppress = stats.getPercentangeSuppress();

                    if (suppress != 0.0) {
                        this.suppressValues(session);
                        this.saveAnonynizationRules(session, response);
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JSONObject jsonAnswer = new JSONObject();
                        jsonAnswer.put("Status", "Fail");
                        jsonAnswer.put("Message", "The solution " + originalSol + " satisfies " + k + "-anonymity so it can not be suppressed!");
                        response.getOutputStream().print(jsonAnswer.toString());
                    }
                }
            } else {
                if (sol != null && !(data instanceof TXTData)) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    JSONObject jsonAnswer = new JSONObject();
                    jsonAnswer.put("Status", "Fail");
                    jsonAnswer.put("Message", "The \"sol\" field needs only fot simple table data!");
                    response.getOutputStream().print(jsonAnswer.toString());
                } else if (sol != null) {
                    this.deleteSuppress(session);
                    sol = sol.trim().replace("[", "").replace("]", "");
                    this.setSelectedNode(session, sol);
                    this.saveAnonynizationRules(session, response);
                } else {
                    this.saveAnonynizationRules(session, response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to produce anonymization rules, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }


    @PostMapping("/clearSession")
    public void clearSession(HttpSession session, HttpServletResponse response) throws IOException {
        try {
            restart(session);
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Success");
            jsonAnswer.put("Message", "Session is cleared!");
            response.getOutputStream().print(jsonAnswer.toString());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to clear session, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }

    @PostMapping("/loadAnonRules")
    public void loadAnonRules(@RequestParam("rules") MultipartFile file, HttpSession session, HttpServletResponse response) throws IOException {
        try {
            this.upload(file, false, session);
            String check = this.loadAnonynizationRules(session, file.getOriginalFilename());
            if (check != null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject jsonAnswer = new JSONObject();
                jsonAnswer.put("Status", "Fail");
                jsonAnswer.put("Message", check);
                response.getOutputStream().print(jsonAnswer.toString());
            } else {
                this.getAnonDataSet(0, 0, session);
                response.setStatus(HttpServletResponse.SC_OK);
                this.saveAnonymizeDataset(session, response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject jsonAnswer = new JSONObject();
            jsonAnswer.put("Status", "Fail");
            jsonAnswer.put("Message", "Unable to load anonymization rules, please try again!");
            response.getOutputStream().print(jsonAnswer.toString());
        }
    }


    //amnesia/dataset
    @PostMapping("/dataset")
    public @ResponseBody
    String Dataset(@RequestParam("file") MultipartFile file,
                   @RequestParam("data") boolean data,
                   @RequestParam("del") String del,
                   @RequestParam("datatype") String datatype,
                   @RequestParam("vartypes") String[] vartypes,
                   @RequestParam("checkColumns") boolean[] checkColumns,
                   HttpSession session) throws Exception {

        this.upload(file, data, session);
        this.getSmallDataSet(del, datatype, "", session);
        this.loadDataset(vartypes, checkColumns, session);

        return null;
    }


    //amnesia/hierarchy
    @PostMapping("/hierarchy")
    public @ResponseBody
    String hierarchy(@RequestParam("file") MultipartFile file,
                     @RequestParam("data") boolean data,
                     HttpSession session) throws Exception {


        this.upload(file, data, session);
        this.loadHierarcy(file.getOriginalFilename(), session);

        return null;
    }


    //amnesia/anonymize
    @PostMapping("/anonymize")
    public @ResponseBody
    String anonymize(@RequestParam("k") int k,
                     @RequestParam("m") int m,
                     @RequestParam("algo") String algo,
                     @RequestParam("relations") String[] relations,
                     HttpSession session) {

        Algorithm algorithm = null;
        Map<Integer, Hierarchy> quasiIdentifiers = new HashMap<>();
        Map<String, Hierarchy> hierarchies;


        hierarchies = (Map<String, Hierarchy>) session.getAttribute("hierarchies");
        Data data = (Data) session.getAttribute("data");

        System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        for (String relation : relations) {
            System.out.println(" relation = " + relation);
        }


        for (int i = 0; i < relations.length; i++) {
            if (!relations[i].equals("") && hierarchies.get(relations[i]) != null) {
                quasiIdentifiers.put(i, hierarchies.get(relations[i]));
            }
        }


        ///////////////////////new feature///////////////////////
        String checkHier;
        for (Map.Entry<Integer, Hierarchy> entry : quasiIdentifiers.entrySet()) {
            Hierarchy h = entry.getValue();
            if (h != null) {
                if (h instanceof HierarchyImplString) {
                    h.syncDictionaries(entry.getKey(), data);
                }

                //problem in hierarchy
                checkHier = h.checkHier(data, entry.getKey());
                if (checkHier != null && !checkHier.endsWith("Ok")) {
                    return checkHier;
                }
                System.out.println("Not Null");
            }
        }

        ////////////////////////////////////////////


        Map<String, Integer> args = new HashMap<>();

        switch (algo) {
            case "Flash":
                args.put("k", k);
                algorithm = new Flash();
                session.setAttribute("algorithm", "flash");
                break;
            case "pFlash":
                args.put("k", k);
                algorithm = new ParallelFlash();
                session.setAttribute("algorithm", "flash");
                break;
            case "kmAnonymity":
            case "apriori":
            case "AprioriShort":
                args.put("k", k);

                //check if m is an integer

                if (k > data.getDataLenght()) {
                    System.out.println("k must be at least as long as the number of records");
                }
                args.put("m", m);

                if (algo.equals("apriori")) {
                    if (!(data instanceof SETData)) {
                        System.out.println("No set-valued dataset loaded!");
                    }
                    algorithm = new Apriori();
                    quasiIdentifiers.get(0).buildDictionary(data.getDictionary());
                }

                session.setAttribute("algorithm", "apriori");

                break;
        }


        algorithm.setDataset(data);
        algorithm.setHierarchies(quasiIdentifiers);

        algorithm.setArguments(args);


        String message = "memory problem";
        try {

            algorithm.anonymize();

        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {

            return message;
        }

        session.setAttribute("quasiIdentifiers", quasiIdentifiers);
        session.setAttribute("k", k);

        if (algorithm.getResultSet() == null) {
            return null;
        } else {

            session.setAttribute("results", algorithm.getResultSet());
            if (!algo.equals("apriori")) {
                Graph graph = algorithm.getLattice();

                session.setAttribute("graph", graph);
            }
        }


        return "ok\n";
    }

    private List<String> findHierarchyType(String file) {
        List<String> result = new ArrayList<>();
        BufferedReader br;
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    break;

                //find if distinct or range hierarchy
                if (line.trim().equalsIgnoreCase("distinct")) {
                    result.add("distinct");
                    continue;
                } else if (line.trim().equalsIgnoreCase("range")) {
                    result.add("range");
                    continue;
                }

                //find if int, double or string
                String[] tokens = line.split(" ");
                if (tokens[0].equalsIgnoreCase("type")) {
                    result.add(tokens[1]);
                }
            }
            br.close();
        } catch (IOException ex) {
            System.out.println("problem");
        }

        return result;
    }

    private boolean checkQuasi(Data dataset, Map<Integer, Hierarchy> quasi, String attrNames) {
        StringBuilder quasiNames = new StringBuilder();
        Map<Integer, String> namesCol = dataset.getColNamesPosition();
        for (Map.Entry<Integer, Hierarchy> entry : quasi.entrySet()) {
            quasiNames.append(namesCol.get(entry.getKey()));
        }
        System.out.println("AttrNames: " + attrNames + " quasiNames: " + quasiNames);
        return attrNames.replaceAll(" ", "").equals(quasiNames.toString().replaceAll(" ", ""));
    }

    private Map<String, String> jsonToMap(String t) {

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> map = null;
        if (t == null) {
            return null;
        }
        try {

            // convert JSON string to Map
            map = mapper.readValue(t, Map.class);
            System.out.println(map);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }
}