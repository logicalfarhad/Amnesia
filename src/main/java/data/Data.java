/*
 * Copyright (C) 2015 "IMIS-Athena R.C.",
 * Institute for the Management of Information Systems, part of the "Athena"
 * Research and Innovation Centre in Information, Communication and Knowledge Technologies.
 * [http://www.imis.athena-innovation.gr/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 */
package data;

import dictionary.DictionaryString;
import exceptions.DateParseException;
import exceptions.LimitException;
import exceptions.NotFoundValueException;
import hierarchy.Hierarchy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * Interface of data
 *
 * @author serafeim
 */
public interface Data {
    int online_rows = 5000;
    String online_version = "daonline";

    double[][] getDataSet();

    void setData(double[][] _data);

    int getDataLenght();

    int getDataColumns();

    void print();

    void exportOriginalData();

    String save(boolean[] checkColumns) throws LimitException, DateParseException, NotFoundValueException;

    void preprocessing() throws LimitException;

    String readDataset(String[] columnTypes, boolean[] checkColumns) throws LimitException, DateParseException, NotFoundValueException;

    void export(String file, Object[][] initialTable, Object[][] anonymizedTable, int[] qids, Map<Integer, Hierarchy> hierarchies, Map<Integer, Set<String>> suppressedValues);

    Map<Integer, String> getColNamesPosition();
//    public Map <Integer,DictionaryString> getDictionary();

    DictionaryString getDictionary();

    //    public void setDictionary(Integer column, DictionaryString dict);
    int getColumnByName(String column);

    String getColumnByPosition(Integer columnIndex);

    String[] getColumnNames();

    //    public void replaceColumnDictionary(Integer column, DictionaryString dict);
    void SaveClmnsAndTypeOfVar(String[] columnTypes, boolean[] checkColumns);

    String findColumnTypes();

    String[][] getSmallDataSet();

    ArrayList<LinkedHashMap> getPage(int pageNum, int numOfRecords);

    String[][] getTypesOfVariables(String[][] smallDataSet);

    int getRecordsTotal();

    Map<Integer, String> getColNamesType();

    String getInputFile();

    SimpleDateFormat getDateFormat(int column);

    void setMask(int column, int[] positions, char character);

}
