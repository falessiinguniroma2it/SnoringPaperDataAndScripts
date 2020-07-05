import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

public class Read {

    public static void main(String[] args) {
        
    	
    	//take files name
    	
    	int i=0;
    	
    	
    	// Prints "Hello, World" to the terminal window.
        String fileProject= "C:\\Users\\Davide\\Google Drive\\Papers\\In progress\\Aalok - Snoring\\OLD\\results\\datasetsv3.csv";
        String names[]= new String[19];
        File datasetNames= new File(fileProject);
        try {
			Scanner inputStream1 = new Scanner(datasetNames);
			
			inputStream1.nextLine();
			
			
			for(; inputStream1.hasNext(); i++)
			{	
				String data= inputStream1.nextLine();
				String[] values = data.split(",");
				names[i]= values [0];
			}	
			
			for(int j=0; j<i ;j++)
     		{
         		System.out.println(names[j]);
     		}
		}catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
        
        
        
        /**********************/
        String path = "C:\\Users\\Davide\\Downloads\\EffectOfSnoring-master\\data\\rq5\\";
        String datasetName =null;
        
        int j=0;
        int k=0;
        double[][]percentArray = new double [19][5];
        DecimalFormat df = new DecimalFormat("#.##");
        for(j=0; j<i ;j++)
 		{
        	for(k=0; k<5 ;k++)
     		{
	        	datasetName = path + names[j] +"_" + String.valueOf(k)+".csv";
		        double countF = 0;
		        double countT = 0;
		        double PercentT =0;
		        System.out.println(datasetName);
		        
		     
		        File datasetFile= new File(datasetName);
		        try {
					Scanner inputStream = new Scanner(datasetFile);
					
					inputStream.nextLine();
					while(inputStream.hasNext())
					{	
						String data= inputStream.nextLine();
						String [] values = data.split(",");
						if(values[18].equals("T")){
							countT +=1;
						}else {
							countF +=1;
						
						}
					}	
				}catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				PercentT = countT / countF;
				if(countF != 0 && countT !=0)
				 {	
					percentArray[j][k] = Double.valueOf(df.format(PercentT)); 
				 }else if (countT == 0 )
				 {	
					 percentArray[j][k] =Double.valueOf(df.format(0));
				 }else {
					 percentArray[j][k] =Double.valueOf(df.format(1));
				 } 
				System.out.println(percentArray[j][k]);
						
     		}
	 	}
    
        try {
			FileWriter myWriter = new FileWriter("C:\\\\Users\\\\Davide\\\\Downloads\\PecentDefective.csv");
			myWriter.write("Project Name, Defective removing 0, Defective removing 1, Defective removing 2, Defective removing 3, Defective removing 4 \n");
			for(j=0; j<i ;j++)
	 		{
					myWriter.write(names[j]);
		        	for(k=0; k<5 ;k++)
		     		{
		        		
		        		myWriter.write(","+percentArray[j][k]);
        	            System.out.println("Successfully wrote a line the file.");
		     		}
		        	myWriter.write("\n");
	 		}
			myWriter.close();
		} catch (IOException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
        
        
        
    }

}
