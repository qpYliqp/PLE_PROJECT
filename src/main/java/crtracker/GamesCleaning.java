package crtracker;
import java.io.IOException;
import java.time.Instant;

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

import java.util.*;
public class GamesCleaning extends Configured implements Tool {

    
   
   
	
    public static class GamesCleaningMapper extends Mapper<LongWritable, Text, Text, Text> {

        
        
        
        // Méthode pour convertir une date ISO 8601 en timestamp (millisecondes)
         
    
        private long convertDateToTimestamp(String date) {
            try {
                // Parse la date en Instant
                java.time.Instant instant = java.time.Instant.parse(date);
                
                // Truncate to the second, removing any fractional seconds
                instant = instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                
                // Retourner le timestamp en millisecondes (arrondi à la seconde près)
                return instant.toEpochMilli();
            } catch (Exception e) {
                return -1;  // Si la date est invalide, retourner -1
            }
        }


        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            Gson gson = new Gson();
            String jsonLine = value.toString();
            Battle battle;
            
            try {
                battle = gson.fromJson(jsonLine, Battle.class);
            } catch (Exception e) {
                return; // Si le JSON est invalide, on l'ignore
            }
        
            // Vérifie que la partie a exactement 2 joueurs
            if (battle.players.size() != 2) return;

        
            String player1 = battle.players.get(0).utag;
            String player2 = battle.players.get(1).utag;


           if (battle.players.get(0).deck.length()!=16 ||  battle.players.get(1).deck.length () !=16   ){
               return;
           }
        
            if (player1 == null || player2 == null) return;
        
            // Tri des joueurs pour garantir l'unicité
            String first = player1.compareTo(player2) <= 0 ? player1 : player2;
            String second = player1.compareTo(player2) > 0 ? player1 : player2;
        
            // Crée une clé unique en incluant la date
         
            String uniqueKey1 = first + "," + second + "," + battle.mode + "," + battle.game + "," + battle.type + "," +  convertDateToTimestamp(battle.date)  ;

          
            context.write(new Text(uniqueKey1), new Text(jsonLine));
        }
    }


   


    public static class GamesCleaningReducer extends Reducer<Text, Text, Text, Text> {
        private HashSet<String> doublonsGlobal = new HashSet<>();

        private String convertDateToTimestamp(String date) {
            try {
                // Parse la date en Instant
                java.time.Instant instant = java.time.Instant.parse(date);
                
                // Truncate to the second, removing any fractional seconds
                instant = instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                
                // Retourner le timestamp en millisecondes (arrondi à la seconde près)
                return String.valueOf(instant.toEpochMilli());
            } catch (Exception e) {
                return null;  // Si la date est invalide, retourner null
            }
        }
        
    
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Gson gson = new Gson();
        
            for (Text value : values) {
                String jsonLine = value.toString(); // Lire chaque ligne JSON
                Battle battle;
        
                try {
                    // Convertir la ligne JSON en objet Battle
                    battle = gson.fromJson(jsonLine, Battle.class);
                } catch (Exception e) {
                    continue; // Ignorer les lignes invalides
                }
        
                // Modifier la date en arrondissant à la seconde près
                String updatedDate = convertDateToTimestamp(battle.date);
                if (updatedDate == null) {
                    continue; // Ignorer les entrées avec des dates invalides
                }
                String updatedJsonLine = gson.toJson(battle);
                if(!doublonsGlobal.add(updatedJsonLine)){
                    continue;
                }
                battle.date = updatedDate;
        
                // Reconvertir l'objet Battle en JSON pour l'écrire dans le contexte
              
                context.write(key, new Text(updatedJsonLine));
            }
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
