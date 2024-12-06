package crtracker;
import java.io.IOException;
import java.sql.Array;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import scala.collection.Iterator;

public class GamesCleaning extends Configured implements Tool {

    


	public static class GamesCleaningMapper	extends Mapper<LongWritable, Text, GameKey, Text> {
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            Gson gson = new Gson();
            String jsonLine = value.toString();

            Battle battle;

            try
            {
                battle = gson.fromJson(jsonLine, Battle.class);
            }catch(Exception e)
            {
                return;
            }

            if(!battle.verifyData())
            {
                return;
            }


            GameKey gameKey = new GameKey(battle);
            context.write(gameKey, new Text(jsonLine));

			
		}
	}

    public static class GamesCleaningReducer extends Reducer<GameKey,Text,Text,Text> {
		public void reduce(Text key, Iterable<Text> values,Context context) throws IOException, InterruptedException {

            Iterator<Text> iterator = (Iterator<Text>) values.iterator();
            if (iterator.hasNext()) {
                Text firstValue = iterator.next();
                context.write(new Text("svp tuez moi :D"), firstValue);
            }
          
           }
	}


    public int run(String args[]) throws IOException, ClassNotFoundException, InterruptedException {
    Configuration conf = getConf();
    Job job = Job.getInstance(conf, "Cleaning games");
    job.setJarByClass(GamesCleaning.class);
    job.setOutputKeyClass(GameKey.class);
    job.setOutputValueClass(Text.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    job.setInputFormatClass(TextInputFormat.class);
    try {
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
    } 
    catch (Exception e) {
        System.out.println(" bad arguments, waiting for 2 arguments [inputURI] [outputURI]");
        return -1;
    }

    job.setMapperClass(GamesCleaningMapper.class);
    job.setReducerClass(GamesCleaningReducer.class);
    return job.waitForCompletion(true) ? 0 : 1;
}

public static void main(String args[]) throws Exception {
    System.exit(ToolRunner.run(new GamesCleaning(), args));
}
    
}
