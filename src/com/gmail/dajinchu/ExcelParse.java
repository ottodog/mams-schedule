package com.gmail.dajinchu;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.format.CellDateFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelParse {
	private static final int MONDAYROW = 2;
	private static final int MONDAYCOL = 3;
	
	
	private XSSFFormulaEvaluator evaluator;
	private XSSFSheet sheet;
	private List<CellRangeAddress> mergedRegions;
	Calendar cal = Calendar.getInstance();
	
	Calendar startCal = Calendar.getInstance();
	Calendar endCal = Calendar.getInstance();
	Calendar monday = Calendar.getInstance();
	Calendar today = Calendar.getInstance();
	
	int FIRSTCOLUMN = 3, FIRSTROW = 6, LASTCOLUMN = 32, LASTROW = 33;
	private int dayoffset;
	
	ArrayList<Section> sections = new ArrayList<Section>();
	Section advF=new Section();
	Section intF=new Section();
	private int firstRow;
	private int firstCol;
	private double sectionNum;
	private Period add;
	private int mergeWidth;
	
	public ExcelParse(String filename){
		try {
			InputStream in = new FileInputStream(filename); // open file buffer
			XSSFWorkbook wbo = new XSSFWorkbook(in); // pass file buffer to workbook
		
			evaluator = wbo.getCreationHelper().createFormulaEvaluator(); // instantiate evaluator, which evaluates excel formulas

			sections.add(new Section());
			sections.add(new Section());
			sections.add(new Section());
			
			for(int i = 2; i<22;i++){
				System.out.println("SHEET " +i);
				sheet = wbo.getSheetAt(i);
				getMetadata();
				getClasses();
			}
			new ICalExporter("A Section").addEvents(sections.get(0));
			new ICalExporter("B Section Advanced").addEvents(advF.merge(sections.get(1)));
			new ICalExporter("B Section Intermediate").addEvents(intF.merge(sections.get(1)));
			new ICalExporter("C Section").addEvents(sections.get(2));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	public void getClasses(){
		
		List<CellRangeAddress> mergedCells = sheet.getMergedRegions();
		Cell c;
		for(CellRangeAddress merge : mergedCells){
			if(!isDuringSchool(merge))continue; //skip this cellrange if it's not during school
			
			firstRow = merge.getFirstRow();
			firstCol = merge.getFirstColumn();
			
			c = sheet.getRow(firstRow).getCell(firstCol);
			
			
			if(c.getCellType()==Cell.CELL_TYPE_STRING){		
				dayoffset = (int)Math.floor((firstCol-3)/6.0);
				sectionNum = ((firstCol-3)%6)/2f;
				
				today.set(monday.get(Calendar.YEAR), 
						monday.get(Calendar.MONTH), 
						monday.get(Calendar.DATE)+dayoffset,
						0,0,0);
				
				try{
					if(evaluator.evaluate(sheet.getRow(firstRow-1).getCell(firstCol)).getCellType()!=Cell.CELL_TYPE_NUMERIC){
						firstRow++;
					}
				}catch(NullPointerException e){
					firstRow++;
				}
				
				System.out.println("trying"+ firstRow+", "+firstCol);
				Date start = sheet.getRow(firstRow-1).getCell(0).getDateCellValue();//TODO PROBLEM
				Date end = sheet.getRow(merge.getLastRow()+1).getCell(0).getDateCellValue();
				startCal.setTime(start);
				endCal.setTime(end);
				//System.out.println(startCal.get(Calendar.HOUR_OF_DAY));
				
				today.set(Calendar.HOUR_OF_DAY,startCal.get(Calendar.HOUR_OF_DAY));
				today.set(Calendar.MINUTE,startCal.get(Calendar.MINUTE));
				if(today.get(Calendar.HOUR)<7){
					today.set(Calendar.AM_PM, Calendar.PM);
				}else{
					today.set(Calendar.AM_PM, Calendar.AM);
				}
				start = today.getTime();
				today.set(Calendar.HOUR_OF_DAY,endCal.get(Calendar.HOUR_OF_DAY));
				today.set(Calendar.MINUTE,endCal.get(Calendar.MINUTE));
				if(today.get(Calendar.HOUR)<7){
					today.set(Calendar.AM_PM, Calendar.PM);
				}else{
					today.set(Calendar.AM_PM, Calendar.AM);
				}
				end = today.getTime();
				
				
				System.out.println("Parsed "+"'"+parseCell(c)+"'"+" fromw row "+firstRow+", col "+firstCol);
				cal.setTime(start);
				System.out.println("  Started at "+calToString(cal));
				cal.setTime(end);
				System.out.println("  Ended at   "+calToString(cal));
								
				String name = parseCell(c);
				
				add = new Period(start,end,name);
				mergeWidth = merge.getLastColumn()-merge.getFirstColumn();
				if(mergeWidth>=2){
					//Whole school event
					sections.get(0).periods.add(add);
					sections.get(1).periods.add(add);
					sections.get(2).periods.add(add);
				}else if(mergeWidth==0){
					if(sectionNum==1){
						advF.periods.add(add);
					}else if(sectionNum==1.5){
						intF.periods.add(add);
					}
				}else{
					sections.get((int)sectionNum).periods.add(add);
				}
			}
		}
	}
	public String calToString(Calendar cal){
		return cal.get(Calendar.MONTH)+"/"+cal.get(Calendar.DAY_OF_MONTH)+"/"+cal.get(Calendar.YEAR)+" "+
				cal.get(Calendar.HOUR_OF_DAY)+":"+cal.get(Calendar.MINUTE)+" PM:"+cal.get(Calendar.AM_PM);
	}
	public boolean isDuringSchool(CellRangeAddress range){
		if(range.getFirstColumn()>=FIRSTCOLUMN &&
				range.getFirstRow()>=FIRSTROW &&
				range.getLastColumn()<=LASTCOLUMN &&
				range.getLastRow()<=LASTROW){
			return true;
		}
		return false;
	}
	public void printAll(){
		Iterator<Row> rowIterator = sheet.iterator(); // iterator containing all rows
		Iterator<Cell> row; // temp variable for iterators across single rows, containing cells
		Cell cell; // temp variable for single cells
		String value; // temp variable for string value inside cells
		DataFormatter df = new DataFormatter();
		while(rowIterator.hasNext()){ // while loop down the list of rows
			row = rowIterator.next().cellIterator();
			while(row.hasNext()){ // loops left to right down a single row
				cell = row.next();
				value = parseCell(cell); // read the cell contents as a string

				if(!value.equals("")){
					System.out.println(value.replaceAll("\n", ""));
				}else if(value.equals("Monday")){
					
				}
				
			}
		}
	}
	public String parseCell(Cell cell){
		
		//If it's a formula and needs to be evaluated
		CellValue cellValue = evaluator.evaluate(cell); // Evaluate references to values
		if(cellValue!=null){
			switch(cellValue.getCellType()){ // different ways to read for numeric and string type cells
				case Cell.CELL_TYPE_NUMERIC:
					double dv = cellValue.getNumberValue();
					if(DateUtil.isCellDateFormatted(cell)){ // account for dates, which mess things up otherwise
						Date date = DateUtil.getJavaDate(dv);
						String dateFormat = cell.getCellStyle().getDataFormatString();
						
						Date d = cell.getDateCellValue();
						cal.setTime(d);
						System.out.println(cal.get(Calendar.MONTH)+" "+cal.get(Calendar.DAY_OF_MONTH));
						System.out.println(cal.get(Calendar.HOUR_OF_DAY)+" "+cal.get(Calendar.MINUTE));
												
						return new CellDateFormatter(dateFormat).format(date)+"";
					}else{
						return dv+"";
					}
				case Cell.CELL_TYPE_STRING:
					return cellValue.getStringValue();
			}
		}

		System.out.println("not evaluate, just reading value"+cell.getCellType());
		//If just a value, just read
		switch(cell.getCellType()){
			case Cell.CELL_TYPE_NUMERIC:
				double dv = cell.getNumericCellValue();
				if(DateUtil.isCellDateFormatted(cell)){
					Date d = cell.getDateCellValue();
					cal.setTime(d);
					System.out.println(cal.get(Calendar.MONTH)+" "+cal.get(Calendar.DAY_OF_MONTH));
					Date date = DateUtil.getJavaDate(dv);
					
					String dateFormat = cell.getCellStyle().getDataFormatString();
					return new CellDateFormatter(dateFormat).format(date)+"";
				}else{
					return dv+"";
				}
			case Cell.CELL_TYPE_STRING:
				return cell.getRichStringCellValue()+"";
			default:
				System.out.println("switch default");
				cell.setCellType(Cell.CELL_TYPE_STRING);
				return cell.getStringCellValue()+"";
		}
	}
	
	public void columnClasses(int row, int col){
		int i = row;
		Cell c;
		while(true){
			c = sheet.getRow(i).getCell(col);
			
		}
	}
	
	/*public Period getPeriod(int row, int col){
		Cell c = sheet.getRow(row).getCell(col);
		String value;
		while((value=parseCell(c))!=null){
			
		}
	}*/
	
	public void getMetadata(){
		Date d = sheet.getRow(MONDAYROW).getCell(MONDAYCOL).getDateCellValue();
		monday.setTime(d);
	}
	
	public static void main(String[] args){
		new ExcelParse("src/15-16.xlsx");
	}
}
