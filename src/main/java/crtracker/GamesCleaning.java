package crtracker;
import java.io.IOException;
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

public class GamesCleaning extends Configured implements Tool {

    


	public static class GamesCleaningMapper	extends Mapper<LongWritable, Text, Text, Text> {
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

            // if (battle.players == null 
            //     || battle.players.size() != 2 
            //     || battle.players.get(0).utag == null
            //     || battle.players.get(1).utag != null 
            //     || battle.players.get(0).deck.length() != 16 || battle.players.get(1).deck.length() != 16
            // ) return;
            if(battle.players.get(0).utag == null)
            {
                return;
            }

            String player1 = battle.players.get(0).utag;
            String player2 = battle.players.get(1).utag;

            String first = player1.compareTo(player2) <= 0 ? player1 : player2;
            String second = player1.compareTo(player2) > 0 ? player1 : player2;
            String uniqueKey = first+ "," + second + "," + battle.mode + "," + battle.game + "," + battle.type;
            


            
            context.write(new Text(uniqueKey), new Text(jsonLine));

			
		}
	}

    public static class GamesCleaningReducer extends Reducer<Text,Text,Text,Text> {
		public void reduce(Text key, Iterable<Text> values,Context context) throws IOException, InterruptedException {
            for(Text line : values)
            {
                context.write(new Text("lol"), new Text(line));
            }

            // for(Text t : values)
            // {
            //     context.write(key, t);
            // }

            
		}
	}


    public int run(String args[]) throws IOException, ClassNotFoundException, InterruptedException {
    Configuration conf = getConf();
    Job job = Job.getInstance(conf, "Clean population");
    job.setJarByClass(GamesCleaning.class);
    job.setOutputKeyClass(Text.class);
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
