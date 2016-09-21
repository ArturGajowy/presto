#!/usr/bin/python

import urllib2

symbols = ['AAL', 'DAL', 'UAL', 'LCC', 'JBLU', 'LUV']

month = 8 # month, starting from zero. 0 = January, 8 = September
day = 21
year = 2016

url = 'http://chart.finance.yahoo.com/table.csv?s={SYMBOL}&d={MONTH}&e={DAY}&f={YEAR}&g=d&ignore=.csv'

for symbol in symbols:
  response = urllib2.urlopen(url.format(SYMBOL=symbol,MONTH=month,DAY=day,YEAR=year))
  content = response.read()
  f = open(symbol + '.csv', 'w')
  f.write(content)
  f.close()
