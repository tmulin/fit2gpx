/*
Copyright © 2015 by Maks Vasilev

created 7.02.2015
http://velo100.ru/garmin-fit-to-gpx

exit code:

0 - ok
64 - help or invalid usage
65 - file invalid
66 - file not found
199 - data read filed
200 - track is empty, but writed (default for console mode)
201 - track is empty and not writed (default for dialog mode)
204 - no file selected
209 - debug break

*/

import com.garmin.fit.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class fit2gpx extends Component {

    public static void main(String[] args) throws IOException {

        File[] MultipleFilesList;
        boolean DialogMode = false;
        boolean StatisticEnable = false;
        boolean OutputCSV = false;
        boolean MonitoringFIT = false;

        Converter converter = new Converter();
        ConverterResult converterResult = new ConverterResult();



        if (args.length == 1) {

            if (String.valueOf(args[0]).equals("--help") || String.valueOf(args[0]).equals("-h")) {
                Help.usage();
            }
            if (String.valueOf(args[0]).equals("--statistic") || String.valueOf(args[0]).equals("-s")) {
                StatisticEnable = true;
            }

            if (String.valueOf(args[0]).equals("--csv") || String.valueOf(args[0]).equals("-c")) {
                OutputCSV = true;
            }

            if (String.valueOf(args[0]).equals("--monitor") || String.valueOf(args[0]).equals("-m")) {
                MonitoringFIT = true;
            }

            if (String.valueOf(args[0]).equals("--hr-only")) {
                OutputCSV = true;
                // FIXME
                converter.setOnlyHRandTime(true);
            }

            if(!StatisticEnable && !OutputCSV && !MonitoringFIT) {
                converter.setInputFITfileName(String.valueOf(args[0]));
                converterResult.add(converter.run(), converter.getInputFITfileName());
            }
        }

        if (args.length == 0 || (args.length == 1 && StatisticEnable) || (args.length == 1 && OutputCSV) || (args.length == 1 && MonitoringFIT) ) {

            DialogMode = true;
            converter.setSaveIfEmpty(true);

            if(OutputCSV) {
                converter.setOutputFormat(0);   // формат вывода CSV
//                System.exit(3);
            }

            if(MonitoringFIT) {
                converter.setOutputFormat(2);   // формат вывода CSV, но будут анализироваться записи мониторинга
//                System.exit(3);
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("FIT -> GPX/CSV: Выберите файл для преобразования в GPX/CSV");
            chooser.setApproveButtonText("Открыть");
            chooser.setApproveButtonToolTipText("Открыть выбранный файл и преобразовать");
            chooser.setMultiSelectionEnabled(true);

            FileNameExtensionFilter filter = new FileNameExtensionFilter("файлы занятий Garmin FIT (.fit)", "FIT", "fit");
            chooser.setFileFilter(filter);

            int returnVal = chooser.showOpenDialog(chooser.getParent());
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                MultipleFilesList = chooser.getSelectedFiles();

                for (File file : MultipleFilesList) {
                    converter.setInputFITfileName(file.getAbsoluteFile().getAbsolutePath());
                    converterResult.add(converter.run(), converter.getInputFITfileName());
                 }

            } else {
                System.exit(204);
            }

          }

        if (args.length > 1) {
            
             if(String.valueOf(args[0]).equals("--statistic") || String.valueOf(args[0]).equals("-s")) {
                StatisticEnable = true;
                
                for(int a = 1; a < args.length; a++) {
                    converter.setInputFITfileName(args[a]);
                    converterResult.add(converter.run(), converter.getInputFITfileName());
                }
            } else {
                for (String arg : args) {
                    converter.setInputFITfileName(arg);
                    converterResult.add(converter.run(), converter.getInputFITfileName());
                }
            }
            
           }

        if(StatisticEnable) {
            System.out.println(converterResult.getSummaryByString());
        }
        
        if(DialogMode) {

            int MessageType = JOptionPane.INFORMATION_MESSAGE;
                    
            if(converterResult.getEmptyFilesCount() > 0) {MessageType = JOptionPane.WARNING_MESSAGE;}
            
            if(converterResult.getBadFilesCount() > 0) {MessageType = JOptionPane.ERROR_MESSAGE;}
            
            JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), converterResult.getSummaryByString(), "Результат конвертирования", MessageType);
        }
 
    }

    private static class Converter {

        private Number semicircleToDegree(Field field) {
            if (field != null && "semicircles".equals(field.getUnits())) {
                final long semicircle = field.getLongValue();
                return semicircle * (180.0 / Math.pow(2.0, 31.0)); // degrees = semicircles * ( 180 / 2^31 )
            } else {
                return null;
            }
        }

        private double round(double d, int p) {
            double dd = Math.pow(10, p);
            return Math.round(d * dd) / dd;
        }

        private final String out_gpx_head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx creator=\"Converted by fit2gpx, http://velo100.ru/garmin-fit-to-gpx from {creator}\" version=\"1.1\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:gpxtrx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" " +
                "xmlns:gpxx=\"http://www.garmin.com/xmlschemas/WaypointExtension/v1\" " +
                "xmlns:nmea=\"http://trekbuddy.net/2009/01/gpx/nmea\">";
        private final String out_gpx_head1 = "\n <metadata>\n  <time>{time}</time>\n </metadata>\n";
        private final String out_gpx_head2 = " <trk>\n  <name>{FTIFile}</name>\n  <trkseg>";
        private final String out_gpx_tail = "\n  </trkseg>\n </trk>\n</gpx>";

        private final String out_csv_head = "time;lat;lon;altitude;enhanced_altitude;speed;enhanced_speed;grade;cadence;fractional_cadence;distance;temperature;calories;heart_rate;" +
                "power;accumulated_power_kJ;left_right_balance;left_right_balance_persent;left_torque_effectiveness;right_torque_effectiveness;left_pedal_smoothness;right_pedal_smoothness;" +
                "left_pco;right_pco;left_power_phase_start;left_power_phase_end;right_power_phase_start;right_power_phase_end;" +
                "left_power_phase_peak_start;left_power_phase_peak_end;right_power_phase_peak_start;right_power_phase_peak_end\n";

        private final String out_hr_head = "time;hr";

        final ArrayList<String> activity = new ArrayList<>();
        private Date TimeStamp = new Date();

        private final SimpleDateFormat DateFormatCSV = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");  // формат вывода в csv
        private final SimpleDateFormat DateFormatGPX = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");  // формат вывода в gpx
        private final SimpleDateFormat NewFileTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");  // формат даты начала, если надо сместить

        private String InputFITfileName;
        private String OutputGPXfileName;

        private FileInputStream InputStream;
        private File InputFITfile;

        private boolean EmptyTrack = true;      // признак того, что трек не содержит координат
        private boolean EmptyLine = true;       // признак того, то отдельная точка не содержит координат
        private boolean SaveIfEmpty = true;     // разрешить сохранение пустого трека без точек
        private Long Local_Timestamp = 0l;      //
        private Long mesgTimestamp;

        private Date FileTimeStamp = new Date();
        private Date NewFileTime = new Date();  // Дата и время начала трека, если необходимо сдвинуть время
        private String DeviceCreator = "";
        
        private long timeOffset = 0L;   // смещение времени, для коррекции треков, в секундах
        private boolean needOffset = false;

        private int OutputFormat = 1;   // формат вывода, по умолчанию 1 = gpx, 0 = csv, 2 = hr-csv
        private boolean OnlyHRandTime = false;

        void setOutputFormat(int outputFormat) {
            OutputFormat = outputFormat;
        }
        void setSaveIfEmpty(boolean saveIfEmpty) {SaveIfEmpty = saveIfEmpty;}
        void setInputFITfileName(String inputFITfileName) {InputFITfileName = String.valueOf(inputFITfileName);}
        void setOnlyHRandTime(boolean onlyHRandTime) {OnlyHRandTime = onlyHRandTime;}

        //public void setOutputGPXfileName(String outputGPXfileName) {OutputGPXfileName = String.valueOf(outputGPXfileName);}
        //public String getOutputGPXfileName() {return OutputGPXfileName;}
        String getInputFITfileName() {return InputFITfileName;}
        
        public void setNewFileTime(String newtime) {
            try {
                NewFileTime = NewFileTimeFormat.parse(newtime);
                needOffset = true;
            } catch (ParseException e) {
                System.err.println("Неверный формат времени для смещения");
                needOffset = false;
                
                //e.printStackTrace();
            }
        }
        
        void setNewOffset(long newOffset) {
            timeOffset = newOffset;
        }

        int run() {  // Основной поэтапный цикл работы конвертера

            int checkStatus = this.check();
            if(checkStatus != 0) {return checkStatus;}

            EmptyTrack = true;

            int readStatus = this.read();
            if(readStatus !=0) {return readStatus;}

            int writeStatus = this.write();
            activity.clear();

            if(writeStatus != 0) {return writeStatus;}

            return 0;
        }

        private int check() {   // этап проверки доступности файла

            Decode decodeс = new Decode();

            try {
                InputFITfile = new File(InputFITfileName);
                InputStream = new FileInputStream(InputFITfile);

            } catch (IOException e) {
                System.err.println("Ошибка: " + InputFITfileName + " не найден или не является файлом!");
                //System.exit(66);
                return 66;
            }

            try {
                if (!decodeс.checkFileIntegrity(InputStream)) {
                    throw new RuntimeException("файл " + InputFITfileName + " повреждён!");
                }
            } catch (RuntimeException e) {
                System.err.print("Ошибка проверки файла: ");
                System.err.println(e.getMessage());

                try {
                    InputStream.close();
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
                //System.exit(65);
                return 65;
            }

            return 0;
        }

        private int read() {    // попытка прочитать входной файл

            Decode decode = new Decode();

            MesgBroadcaster mesgBroadcaster = new MesgBroadcaster(decode);

            FileIdMesgListener fileIdMesgListener = new FileIdMesgListener() {
                @Override
                public void onMesg(FileIdMesg mesg) {
                    
                    String _Product = "";
                    String _Manufacturer = "";

                    if (mesg.getTimeCreated() != null) {
                        
                        if(needOffset) {
                            setNewOffset((NewFileTime.getTime() / 1000) - mesg.getTimeCreated().getTimestamp() - (DateTime.OFFSET / 1000));
                        }

                        FileTimeStamp = new Date(mesg.getTimeCreated().getTimestamp() * 1000 + DateTime.OFFSET + (timeOffset * 1000));
                    }

                    if (mesg.getManufacturer() != null) {
                        _Manufacturer = " (" + FitTools.manufacturerById(mesg.getManufacturer()) + ")";
                    }
                    
                    if (mesg.getProduct() != null) {
                        _Product = FitTools.productById(mesg.getProduct()) + _Manufacturer;
                    }
                    DeviceCreator = _Product;
                }
            };

            MesgListener mesgListener = new MesgListener() {

                @Override
                public void onMesg(Mesg mesg) {

                    String line = "";
                    EmptyLine = true;

                        switch (OutputFormat) {

                            case 1:

                                if (mesg.getFieldStringValue("timestamp") != null) {

                                    TimeStamp = new Date((mesg.getFieldLongValue("timestamp") * 1000) + DateTime.OFFSET + (timeOffset * 1000));

                                    final Number lat = semicircleToDegree(mesg.getField("position_lat"));
                                    final Number lon = semicircleToDegree(mesg.getField("position_long"));

                                    if (lat != null && lon != null) {

                                        EmptyTrack = false;
                                        EmptyLine = false;

                                        line += "\n   <trkpt lat=\"" + lat.toString() + "\" lon=\"" + lon.toString() + "\">\n" +
                                                "    <time>" + DateFormatGPX.format(TimeStamp) + "</time>";

                                    } else {
                                        line += "\n   <trkpt lat=\"\" lon=\"\">\n" +
                                                "    <time>" + DateFormatGPX.format(TimeStamp) + "</time>";
                                        EmptyLine = true;
                                    }


                                    if (mesg.getFieldStringValue("altitude") != null) {
                                        line += "\n    <ele>" + round(mesg.getFieldDoubleValue("altitude"), 3) + "</ele>";
                                        EmptyLine = false;
                                    }

                                    if (mesg.getFieldStringValue("temperature") != null || mesg.getFieldStringValue("heart_rate") != null
                                            || mesg.getFieldStringValue("cadence") != null || mesg.getFieldStringValue("speed") != null
                                            || mesg.getFieldStringValue("distance") != null) {

                                        EmptyLine = false;

                                        line += "\n    <extensions>";

                                        if (mesg.getFieldStringValue("speed") != null) {
                                            line += "\n     <nmea:speed>" + mesg.getFieldStringValue("speed") + "</nmea:speed>";
                                        }
                                        line += "\n     <gpxtpx:TrackPointExtension>";

                                        if (mesg.getFieldStringValue("temperature") != null) {
                                            line += "\n      <gpxtpx:atemp>" + mesg.getFieldStringValue("temperature") + "</gpxtpx:atemp>";
                                        }
                                        if (mesg.getFieldStringValue("heart_rate") != null) {
                                            line += "\n      <gpxtpx:hr>" + mesg.getFieldStringValue("heart_rate") + "</gpxtpx:hr>";
                                        }
                                        if (mesg.getFieldStringValue("cadence") != null) {
                                            line += "\n      <gpxtpx:cad>" + mesg.getFieldStringValue("cadence") + "</gpxtpx:cad>";
                                        }
                                        if (mesg.getFieldStringValue("speed") != null) {
                                            line += "\n      <gpxtpx:speed>" + mesg.getFieldStringValue("speed") + "</gpxtpx:speed>";
                                        }
                                        if (mesg.getFieldStringValue("distance") != null) {
                                            line += "\n      <gpxtpx:course>" + mesg.getFieldStringValue("distance") + "</gpxtpx:course>";
                                        }

                                        line += "\n     </gpxtpx:TrackPointExtension>\n    </extensions>";
                                    }

                                    line += "\n   </trkpt>";

                                    if (!EmptyLine) {
                                        activity.add(line);
                                    }
                                }
                                break;

                            case 0:

                                 if (mesg.getFieldStringValue("timestamp") != null) {

                                    TimeStamp = new Date((mesg.getFieldLongValue("timestamp") * 1000) + DateTime.OFFSET + (timeOffset * 1000));

                                     if(!OnlyHRandTime) {

                                         final Number lat = semicircleToDegree(mesg.getField("position_lat"));
                                         final Number lon = semicircleToDegree(mesg.getField("position_long"));

                                         if (lat != null && lon != null) {

                                             EmptyTrack = false;
                                             EmptyLine = false;

                                             line += DateFormatGPX.format(TimeStamp) + ";" + lat.toString() + ";" + lon.toString() + ";";
                                         } else {
                                             line += DateFormatGPX.format(TimeStamp) + ";;;";

                                             EmptyLine = true;
                                         }
                                     } else {
                                         line += DateFormatCSV.format(TimeStamp) + ";";
                                         EmptyTrack = false;
                                     }

                                     if(!OnlyHRandTime) {

                                         if (mesg.getFieldStringValue("altitude") != null) {
                                             line += mesg.getFieldDoubleValue("altitude") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("enhanced_altitude") != null) {
                                             line += mesg.getFieldDoubleValue("enhanced_altitude") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("speed") != null) {
                                             line += mesg.getFieldDoubleValue("speed") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("enhanced_speed") != null) {
                                             line += mesg.getFieldDoubleValue("enhanced_speed") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("grade") != null) {
                                             line += mesg.getFieldDoubleValue("grade") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("cadence") != null) {
                                             line += mesg.getFieldStringValue("cadence") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("fractional_cadence") != null) {
                                             line += mesg.getFieldDoubleValue("fractional_cadence") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("distance") != null) {
                                             line += mesg.getFieldDoubleValue("distance") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("temperature") != null) {
                                             line += mesg.getFieldStringValue("temperature") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("calories") != null) {
                                             line += mesg.getFieldStringValue("calories") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }
                                     }

                                     if (mesg.getFieldStringValue("heart_rate") != null) {
                                         line += mesg.getFieldStringValue("heart_rate");
                                         if(!OnlyHRandTime) {
                                             line += ";";
                                         }
                                         EmptyLine = false;
                                     } else {
                                         line += ";";
                                     }

                                     if(!OnlyHRandTime) {

                                         if (mesg.getFieldStringValue("power") != null) {
                                             line += mesg.getFieldStringValue("power") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("accumulated_power") != null) {
                                             line += mesg.getFieldDoubleValue("accumulated_power") / 1000.0 + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("left_right_balance") != null) {
                                             if (mesg.getFieldDoubleValue("left_right_balance") >= 0.0 && mesg.getFieldDoubleValue("left_right_balance") <= 360.0) {
                                                 // line += mesg.getFieldShortValue(30, 0, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";"; //
                                                 line += mesg.getFieldStringValue("left_right_balance") + ";";
                                                 line += (mesg.getFieldDoubleValue("left_right_balance") / 3.6) - 50.0 + ";";
                                                 EmptyLine = false;
                                             }
                                         } else {
                                             line += ";;";
                                         }

                                         if (mesg.getFieldStringValue("left_torque_effectiveness") != null) {
                                             line += mesg.getFieldDoubleValue("left_torque_effectiveness") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("right_torque_effectiveness") != null) {
                                             line += mesg.getFieldDoubleValue("right_torque_effectiveness") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("left_pedal_smoothness") != null) {
                                             line += mesg.getFieldDoubleValue("left_pedal_smoothness") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("right_pedal_smoothness") != null) {
                                             line += mesg.getFieldDoubleValue("right_pedal_smoothness") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("left_pco") != null) {
                                             line += mesg.getFieldStringValue("left_pco") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getFieldStringValue("right_pco") != null) {
                                             line += mesg.getFieldStringValue("right_pco") + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getNumFieldValues(69, Fit.SUBFIELD_INDEX_MAIN_FIELD) == 2) {  //  left_power_phase
                                             line += mesg.getFieldFloatValue(69, 0, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             line += mesg.getFieldFloatValue(69, 1, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getNumFieldValues(71, Fit.SUBFIELD_INDEX_MAIN_FIELD) == 2) {  //  right_power_phase
                                             line += mesg.getFieldFloatValue(71, 0, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             line += mesg.getFieldFloatValue(71, 1, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getNumFieldValues(70, Fit.SUBFIELD_INDEX_MAIN_FIELD) == 2) {  //  left_power_phase_peak
                                             line += mesg.getFieldFloatValue(70, 0, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             line += mesg.getFieldFloatValue(70, 1, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }

                                         if (mesg.getNumFieldValues(72, Fit.SUBFIELD_INDEX_MAIN_FIELD) == 2) {  //  right_power_phase_peak
                                             line += mesg.getFieldFloatValue(72, 0, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             line += mesg.getFieldFloatValue(72, 1, Fit.SUBFIELD_INDEX_MAIN_FIELD) + ";";
                                             EmptyLine = false;
                                         } else {
                                             line += ";";
                                         }
                                     }


                                    if (!EmptyLine) {
                                        line += "\n";
                                        activity.add(line);
                                    }
                                }
                                break;

                            case 2:

                                if (mesg.getFieldStringValue("timestamp") != null) {
                                    mesgTimestamp = mesg.getFieldLongValue("timestamp");
                                } else if(mesg.getFieldStringValue("timestamp_16") != null) {
                                    mesgTimestamp += ( mesg.getFieldLongValue("timestamp_16") - ( mesgTimestamp & 0xFFFF ) ) & 0xFFFF;
                                }

                                if (mesg.getFieldStringValue("timestamp_16") != null && mesg.getFieldStringValue("heart_rate") != null && mesg.getFieldIntegerValue("heart_rate") > 20) {

                                    TimeStamp = new Date((mesgTimestamp * 1000) + DateTime.OFFSET + (timeOffset * 1000));

                                    line += DateFormatCSV.format(TimeStamp) + ";" + mesg.getFieldStringValue("heart_rate");
                                    line += "\n";
                                    // System.out.print("\n" + Local_Timestamp + ";" + DateFormatCSV.format(TimeStamp) + ";" + TimeStamp + ";" + mesgTimestamp + ";" + mesg.getFieldLongValue("timestamp_16"));
                                    EmptyLine = false;
                                    EmptyTrack = false;

                                }

                                if (!EmptyLine) {
                                    activity.add(line);
                                }
                                break;
                        }
                    }
            };
            
            mesgBroadcaster.addListener(fileIdMesgListener);
            mesgBroadcaster.addListener(mesgListener);
            
            try {
                mesgBroadcaster.run(new BufferedInputStream(new FileInputStream(InputFITfile)));
            } catch (FitRuntimeException e) {
                System.err.print("Ошибка обработки файла " + InputFITfile + ": ");
                System.err.println(e.getMessage());

                try {
                    InputStream.close();
                } catch (IOException f) {
                    throw new RuntimeException(f);
                }

                return 199;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                InputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
            switch (OutputFormat) {
                case 0:
                    if(!OnlyHRandTime) {
                        activity.add(0, out_csv_head);
                    }
                    break;
                case 1:
                    activity.add(0, out_gpx_head.replace("{creator}", DeviceCreator));
                    activity.add(1, out_gpx_head1.replace("{time}", DateFormatGPX.format(FileTimeStamp)));
                    activity.add(2, out_gpx_head2.replace("{FTIFile}", InputFITfile.getName()));
                    activity.add(out_gpx_tail);
                    break;
                case 2:
                    //activity.add(0, out_hr_head); // пишем без заголовка для склеивания файлов
                    break;
            }

            return 0;
        }

        private int write() {   // попытка записать выходной файл

            if (EmptyTrack && !SaveIfEmpty) {
                return 201;
                // System.exit(200);
            }

            if (EmptyTrack) {
                OutputGPXfileName = InputFITfileName + ".empty";
            } else {

                switch (OutputFormat) {
                    case 0:
                        if(!OnlyHRandTime) {
                            OutputGPXfileName = InputFITfileName + ".csv";
                        } else {
                            OutputGPXfileName = InputFITfileName + ".cut.csv";
                        }
                        break;
                    case 1:
                        OutputGPXfileName = InputFITfileName + ".gpx";
                        break;
                    case 2:
                        OutputGPXfileName = InputFITfileName + ".monitor.csv";
                        break;

                }
            }

            try {

                File OutputGPXfile = new File(OutputGPXfileName);

                if (!OutputGPXfile.exists()) {
                    if(!OutputGPXfile.createNewFile()) {return 73;}
                }

                PrintWriter OutWriter = new PrintWriter(OutputGPXfile.getAbsoluteFile());

                try {

                    activity.forEach(OutWriter::write);

                } finally {
                    OutWriter.close();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
                //System.exit(73);
            }

            if (EmptyTrack) {return 200;}

            return 0;
        }

    }

    private static class ConverterResult {
        private final ArrayList<String> GoodFiles = new ArrayList<>();
        private final ArrayList<String> EmptyFiles = new ArrayList<>();
        private final ArrayList<String> BadFiles = new ArrayList<>();

        //public int getGoodFilesCount() {return GoodFiles.size();}
        int getEmptyFilesCount() {return EmptyFiles.size();}
        int getBadFilesCount() {return BadFiles.size();}

        void add(int result, String file) {
            if(result == 0) {GoodFiles.add(file);}
            if(result == 200 || result == 201) {EmptyFiles.add(file);}
            if(result == 65) {BadFiles.add(file + " - файл повреждён");}
            if(result == 66) {BadFiles.add(file + " - файл не найден");}
            if(result == 73) {BadFiles.add(file + " - ошибка сохранения файла");}
            if(result == 199) {BadFiles.add(file + " - ошибка чтения данных из файла");}
        }

        String getSummaryByString() {

            String result = "Успешно обработано файлов: " + GoodFiles.size();
            if(GoodFiles.size() < 11) {
                for (String GoodFile : GoodFiles) {
                    result += "\n    " + GoodFile;
                }
            } else {
                for(int g = 0; g < 11; g++) {
                    result += "\n    " + GoodFiles.get(g);
                }
                result += "\n  … ещё файлов: " + String.valueOf(GoodFiles.size() - 10);
            }

            result += "\n\nФайлов без треков: " + EmptyFiles.size();
            if(EmptyFiles.size() < 11) {
                for (String EmptyFile : EmptyFiles) {
                    result += "\n    " + EmptyFile;
                }
            } else {
                for(int g = 0; g < 11; g++) {
                    result += "\n    " + EmptyFiles.get(g);
                }
                result += "\n  … ещё файлов: " + String.valueOf(EmptyFiles.size() - 10);
            }

            result += "\n\nФайлов с ошибками: " + BadFiles.size();
            if(BadFiles.size() < 11) {
                for (String BadFile : BadFiles) {
                    result += "\n    " + BadFile;
                }
            } else {
                for(int g = 0; g < 11; g++) {
                    result += "\n    " + BadFiles.get(g);
                }
                result += "\n  … ещё файлов: " + String.valueOf(BadFiles.size() - 10);
            }

            return result;
        }

    }

}

/*
TODO: 1) учёт временной зоны
TODO: 2) возможноть более широкой конфигурации



 */