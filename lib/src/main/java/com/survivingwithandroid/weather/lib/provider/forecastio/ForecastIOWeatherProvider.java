package com.survivingwithandroid.weather.lib.provider.forecastio;

import android.location.Location;

import com.survivingwithandroid.weather.lib.WeatherConfig;
import com.survivingwithandroid.weather.lib.exception.ApiKeyRequiredException;
import com.survivingwithandroid.weather.lib.exception.WeatherLibException;
import com.survivingwithandroid.weather.lib.model.BaseWeather;
import com.survivingwithandroid.weather.lib.model.City;
import com.survivingwithandroid.weather.lib.model.CurrentWeather;
import com.survivingwithandroid.weather.lib.model.DayForecast;
import com.survivingwithandroid.weather.lib.model.HistoricalWeather;
import com.survivingwithandroid.weather.lib.model.HourForecast;
import com.survivingwithandroid.weather.lib.model.Weather;
import com.survivingwithandroid.weather.lib.model.WeatherForecast;
import com.survivingwithandroid.weather.lib.model.WeatherHourForecast;
import com.survivingwithandroid.weather.lib.provider.IWeatherCodeProvider;
import com.survivingwithandroid.weather.lib.provider.IWeatherProvider;
import com.survivingwithandroid.weather.lib.request.Params;
import com.survivingwithandroid.weather.lib.request.WeatherRequest;
import com.survivingwithandroid.weather.lib.util.WeatherUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

/**
 * ${copyright}.
 */

/**
 * This class implements ForecastIO API retrieving weather information from https://developer.forecast.io/docs/v2.
 * One important this to notice is in cityId must be passed lat#lon.
 * This provider doesn't implement a way to look for a city but it requires you use the latitude and logitude,
 * so you have to pass it directly into the cityId.
 * You can use {@link android.location.Geocoder} to get the geographic coordinates from the city name.
 *
 * @author Francesco Azzola
 * @author Stpehen A. Gutknecht
 * */
public class ForecastIOWeatherProvider implements IWeatherProvider {

    private static final String URL = "https://api.forecast.io/forecast/";
    private static final long EXPIRE_TIME = 5 * 60 * 1000; // 5 min
    private WeatherConfig config;
    private CurrentWeather cWeather;
    private WeatherHourForecast whf;
    private WeatherForecast forecast;
    private long lastUpdate;


    private BaseWeather.WeatherUnit units = new BaseWeather.WeatherUnit();

    @Override
    public CurrentWeather getCurrentCondition(String data) throws WeatherLibException {
        if (cWeather != null && !isExpired())
            return cWeather;
        else {
            parseData(data);
            return cWeather;
        }
    }

    @Override
    public WeatherForecast getForecastWeather(String data) throws WeatherLibException {
        if (forecast != null && !isExpired())
            return forecast;
        else {
            parseData(data);
            return forecast;
        }

    }

    @Override
    public List<City> getCityResultList(String data) throws WeatherLibException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WeatherHourForecast getHourForecastWeather(String data) throws WeatherLibException {
        if (whf != null && !isExpired())
            return whf;
        else {
            parseData(data);
            return  whf;
        }
    }


    @Override
    public String getQueryCityURL(String cityNamePattern) throws ApiKeyRequiredException {
        return null;
    }
    /*
        @Override
        public String getQueryCurrentWeatherURL(String cityId) throws ApiKeyRequiredException {
            return createURL(cityId);
        }

        @Override
        public String getQueryForecastWeatherURL(String cityId) throws ApiKeyRequiredException {
            return createURL(cityId);
        }

        @Override
        public String getQueryHourForecastWeatherURL(String cityId) throws ApiKeyRequiredException {
          return createURL(cityId);
        }
*/
        @Override
        public HistoricalWeather getHistoricalWeather(String data) throws WeatherLibException {
            return null;
        }


    @Override
    public String getQueryCityURLByLocation(Location location) throws ApiKeyRequiredException {
        return null;
    }

    @Override
    public String getQueryCityURLByCoord(double lon, double lat) throws ApiKeyRequiredException {
        return null;
    }

    @Override
    public void setConfig(WeatherConfig config) {
        this.config = config;
        units = WeatherUtility.createWeatherUnit(config.unitSystem);
    }

    @Override
    public void setWeatherCodeProvider(IWeatherCodeProvider codeProvider) {

    }

    @Override
    public String getQueryImageURL(String weatherId) throws ApiKeyRequiredException {
        return null;
    }


    @Override
    public String getQueryHistoricalWeatherURL(WeatherRequest request, Date startDate, Date endDate) throws ApiKeyRequiredException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getQueryLayerURL(String cityId, Params params) throws ApiKeyRequiredException {
        return null;
    }

    private String createURL(WeatherRequest request)throws ApiKeyRequiredException {
        if (config.ApiKey == null || config.ApiKey.equals(""))
            throw new ApiKeyRequiredException();

        //StringTokenizer st = new StringTokenizer(cityId, "#");

        return URL + config.ApiKey + "/" + request.getLat() + "," + request.getLon() + "?units=" + (WeatherUtility.isMetric(config.unitSystem) ? "ca" : "us") ;
    }


    // ToDo: make this an App-wide (other providers) convention
    public static final double TEMPERATURE_LOW_OUTRANGE_DOUBLE = -273.15D;  // Absolute Zero, Kelvin 0 - in Celsius
    public static final double TEMPERATURE_HIGH_OUTRANGE_DOUBLE = 660.3D;   // Boiling water should be hot enough, although altitude... and space stations... so using melting point of Aluminum in Celsius

    private void parseData(String data)  throws WeatherLibException {
        lastUpdate = System.currentTimeMillis();

        cWeather = new CurrentWeather();
        Weather weather = new Weather();
        try {
            // We create out JSONObject from the data
            JSONObject rootObj = new JSONObject(data);

            // Parse city
            com.survivingwithandroid.weather.lib.model.Location loc = new com.survivingwithandroid.weather.lib.model.Location();
            loc.setLatitude((float)  rootObj.getDouble("latitude"));
            loc.setLongitude((float) rootObj.getDouble("longitude"));

            weather.location = loc;

            // Parse current weather
            JSONObject currently = rootObj.getJSONObject("currently");

            // Modify myself
            weather = parseWeather(currently, weather);
            cWeather.weather = weather;
            cWeather.setUnit(units);

            // Hourly Weather
            JSONObject hourly = rootObj.getJSONObject("hourly");

            whf = new WeatherHourForecast();
            JSONArray jsonHourlyDataArray = hourly.getJSONArray("data");
            for (int i=0; i < jsonHourlyDataArray.length(); i++) {
                JSONObject jsonHour = jsonHourlyDataArray.getJSONObject(i);
                // Start with an empty weather object
                Weather hWeather = parseWeather(jsonHour, new Weather());
                HourForecast hourForecast = new HourForecast();
                hourForecast.timestamp = jsonHour.optLong("time");
                hourForecast.weather = hWeather;

                whf.addForecast(hourForecast);
            }

            whf.setUnit(units);

            // Day forecast
            JSONObject daily = rootObj.getJSONObject("daily");

            // ToDo: check for null daily before blindly getting the data

            forecast = new WeatherForecast();

            JSONArray jsonDailyDataArray = daily.getJSONArray("data");
            for (int i=0; i < jsonDailyDataArray.length(); i++) {
                // Pull from the DAILY object
                JSONObject jsonDay = jsonDailyDataArray.getJSONObject(i);
                android.util.Log.d("WeatherA", "day " + i + "? " + jsonDay.toString());

                // Pull the sunrise/sunset for today, day 0, out of the first day forecast
                // Pull the min and maximum forecast for today, day 0
                // ToDo: could we pull moon phase, etc? Should we just never touch the "current" json root at all?
                if (i == 0) {
                    loc.setSunrise(jsonDay.optLong("sunriseTime"));
                    loc.setSunset(jsonDay.optLong("sunriseTime"));
                    // Salvage the high and low which is not in "currently" data
                    Double day0Min = jsonDay.optDouble("temperatureMin", TEMPERATURE_LOW_OUTRANGE_DOUBLE);
                    if (day0Min.floatValue() > weather.temperature.getMinTemp())
                        weather.temperature.setMinTemp(day0Min.floatValue());
                    else
                        android.util.Log.d("WeatherA", "MIN day0? " + jsonDay.toString());

                    Double day0Max = jsonDay.optDouble("temperatureMax", TEMPERATURE_HIGH_OUTRANGE_DOUBLE);
                    if (day0Max.floatValue() < weather.temperature.getMaxTemp())
                        weather.temperature.setMaxTemp(day0Max.floatValue());
                    else
                        android.util.Log.d("WeatherA", "MAX day0? " + jsonDay.toString());

                    android.util.Log.d("WeatherA", "temperature got day0Min: " + day0Min + " max: " + day0Max + " sunriseTime: " + jsonDay.optLong("sunriseTime"));
                }

                // Start with an empty weather object
                Weather hWeather = parseWeather(jsonDay, new Weather());
                DayForecast dayForecast = new DayForecast();
                dayForecast.timestamp = jsonDay.optLong("time");
                dayForecast.weather = hWeather;

                forecast.addForecast(dayForecast);
            }


            forecast.setUnit(units);
        }
        catch (JSONException json) {
            json.printStackTrace();
            throw new WeatherLibException(json);
        }

        //cWeather.setUnit(units);
        cWeather.weather = weather;
    }


    /*
    Attempt to reuse the same data structure for current/minute/hour/day that Forecast.IO provides
    "currently":{"time":1416862104,"summary":"Clear","icon":"clear-day","nearestStormDistance":756,"nearestStormBearing":310,"precipIntensity":0,"precipProbability":0,"temperature":17.96,"apparentTemperature":17.96,"dewPoint":0.3,"humidity":0.3,"windSpeed":8.79,"windBearing":344,"visibility":16.09,"cloudCover":0.11,"pressure":1018.22,"ozone":307.8},
    "daily" {"time":1416808800,"summary":"Clear throughout the day.","icon":"clear-day","sunriseTime":1416834337,"sunsetTime":1416871943,"moonPhase":0.08,"precipIntensity":0,"precipIntensityMax":0,"precipProbability":0,"temperatureMin":7.92,"temperatureMinTime":1416834000,"temperatureMax":17.99,"temperatureMaxTime":1416862800,"apparentTemperatureMin":6.64,"apparentTemperatureMinTime":1416834000,"apparentTemperatureMax":17.99,"apparentTemperatureMaxTime":1416862800,"dewPoint":2.09,"humidity":0.52,"windSpeed":7.9,"windBearing":340,"visibility":16.09,"cloudCover":0.05,"pressure":1017.32,"ozone":304.24},
     */
    private Weather parseWeather(JSONObject jsonWeather, Weather weather) throws JSONException {
        // Weather weather = new Weather();
        weather.currentCondition.setDescr(jsonWeather.optString("summary"));
        weather.currentCondition.setIcon(jsonWeather.optString("icon"));
        String normalizedCondition = jsonWeather.optString("icon");
        // Without this, we are just ignoring the field and getting null. So fake it.
        // Get "clear-night"
        if (normalizedCondition != null)
            normalizedCondition = normalizedCondition.replace("-day", " ").replace("-night", "");
        weather.currentCondition.setCondition(normalizedCondition);

        weather.rain[0].setAmmount((float) jsonWeather.optDouble("precipIntensity"));
        weather.rain[0].setChance((float) jsonWeather.optDouble("precipProbability"));

        // The idea that the temperatures are OPTIONAL in the JSON is likely not a good idea.
        weather.temperature.setTemp((float) jsonWeather.optDouble("temperature"));
        // Design issue here that 0 as a default value looks too much like a legitimate temperature
        Double minTempIn = jsonWeather.optDouble("temperatureMin", TEMPERATURE_LOW_OUTRANGE_DOUBLE);  // Absolute Zero, Kelvin 0
        if (minTempIn == null)
        {
            minTempIn = TEMPERATURE_LOW_OUTRANGE_DOUBLE;   // Absolute Zero, Kelvin 0
        }
        weather.temperature.setMinTemp((float) minTempIn.doubleValue());
        Double maxTempIn = jsonWeather.optDouble("temperatureMax", TEMPERATURE_HIGH_OUTRANGE_DOUBLE);  // Boiling water should be hot enough, although altitude... and space stations... so using melting point of Aluminum
        if (maxTempIn == null)
        {
            maxTempIn = TEMPERATURE_HIGH_OUTRANGE_DOUBLE;  // Boiling water should be hot enough, although altitude... and space stations... so using melting point of Aluminum
        }
        weather.temperature.setMaxTemp((float) maxTempIn.doubleValue());
        weather.currentCondition.setDewPoint((float) jsonWeather.optDouble("dewPoint"));

        weather.wind.setSpeed((float) jsonWeather.optDouble("windSpeed"));
        weather.wind.setDeg((float) jsonWeather.optDouble("windBearing"));

        weather.clouds.setPerc((int) jsonWeather.optDouble("cloudCover") * 100); // We transform it in percentage
        weather.currentCondition.setHumidity((int) jsonWeather.optDouble("humidity") * 100);  // We transform it in percentage
        weather.currentCondition.setVisibility((float) jsonWeather.optDouble("visibility"));
        weather.currentCondition.setPressure((float) jsonWeather.optDouble("pressure"));

        weather.location.setSunrise(jsonWeather.optLong("sunriseTime"));
        weather.location.setSunrise(jsonWeather.optLong("sunsetTime"));

        com.survivingwithandroid.weather.lib.model.Location.Astronomy currentAstro = weather.location.getAstronomy();
        currentAstro.percIllum = jsonWeather.optString("moonPhase");
        weather.location.setAstronomy(currentAstro);

        return weather;
    }

    private boolean isExpired() {
        if (lastUpdate == 0)
            return true; // First time;

        if (lastUpdate - System.currentTimeMillis() > EXPIRE_TIME)
            return true;

        return false;
    }

    // New methods

    @Override
    public String getQueryCurrentWeatherURL(WeatherRequest request) throws ApiKeyRequiredException {
        return createURL(request);
    }

    @Override
    public String getQueryForecastWeatherURL(WeatherRequest request) throws ApiKeyRequiredException {
        return createURL(request);
    }

    @Override
    public String getQueryHourForecastWeatherURL(WeatherRequest request) throws ApiKeyRequiredException {
        return null;
    }


}
