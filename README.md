# Climate monitor

Make sure to create `local.properties` file and add the following to it:

```
SENSOR_URL=YOUR_DATA_URL_NO_STRING_QUOTES
```

That URL is what the widget will ping to get the temperature, humidty and CO2 levels. The URL must return data in the following format:

```
{
  "temperature": 25,
  "humidity": 45,
  "co2": 812
}
```
