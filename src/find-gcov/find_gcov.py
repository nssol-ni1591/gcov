#!/usr/bin/python
# -*- coding: utf-8 -*-
# -*- vi: set ts=4 sts=4 sw=4 : -*-

import os
import re
import subprocess
import sys

class find_gcov :

	CATELIST = { }
	CACHE_CATEGORY = [ ]
	CACHE_UNIT = ""

	CATEGORY_NOT_FOUND = [ "-", "-", "-", "-", "-" ]

#	PATTERN1 = re.compile("(\w*)\t([\w\/]*)\/bin\/([\w\/]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)$")
	PATTERN2 = re.compile("([\w\/]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\w\.\-]*)$")

	PATTERN3 = re.compile("Function '(.*)'")
	PATTERN4 = re.compile("^Lines executed:([\d\.]+)% ([\d]+)")
	PATTERN5 = re.compile("^File '(.*)'")
	PATTERN6 = re.compile("^([\w]* )*([\w\*:&]* )?(_|std::)")

	PATTERN7 = re.compile("^\s\/\*.*\*\/\s*$")
	PATTERN8 = re.compile("^\s*\/\*")
	PATTERN9 = re.compile("^.*\*\/\s*$")
	PATTERN10 = re.compile("^(#.*|\s*)([{}]\s*|\/\/.*)?$")

	def debug(msg) :
#		print msg
		return

	def warning(msg) :
		print >> sys.stderr, "Warning: %s" % msg
		return

	def make_projlist(self, path) :
		with open(path, "r") as ins :
			for rec in ins :
				rec = rec.strip()

				self.debug("make_projlist: rec=[%s]" % rec)
				if len(rec) > 0 and rec[0] == "#" :
					continue

				result2 = self.PATTERN2.match(rec)
				if result2 :
					unit = result2.group(2)
					category = [result2.groiup(2), result2.groiup(3), result2.groiup(4), result2.groiup(5)]
					component = result2.groiup(6)
				else :
					self.warning("mapping file format. (rec=[%s])" % rec)
					continue

				self.debug("make_projlist: unit=[%s] category=[%s] component=[%s]" % (unit, " ".join(category), component))
				category.append(component)
				self.CATELIST[unit] = category

	def path_to_category(self, path) :
		if self.CACHE_UNIT != "" and path.startswith(self.CACHE_UNIT) :
#			self.debug("path_to_category: unit=[%s] category=[%s] cached" % (self.CACHE_UNIT, " ".joun(" ", self.CAHCE_CATEGORY)))
			return self.CACHE_CATEGORY

		for unit in self.CATELIST.keys() :
			pos = path.find(unit + "/")
			if pos > 0 :
				self.CAHE_CATEGORY = self.CATELIST[unit]
				self.CACHE_UNIT = unit
				self.debug("path_to_category: unit=[%s] category=[%s]" % (selt.CACHE_UNIT, " ".join(CACHE_CATEGORY)))
				return CACHE_CATEGORY

		self.warning("category not found. (path=[%s]" % path)
		self.CACHE_UNIT = ""
		return self.CATEGORY_NOT_FOUND

	def gcov_csv(self, path) :
		cmd = "LANG=C gcov -r -p -n -f %s | c++filt" % path
		gcov = subprocess.Popen(cmd, shell = True, stdout = subprocess.PIPE)
		for rec in inter(gcov.stdout.readline, b'') :
			rec = rec.rstrip("\n")

			self.debug("gcov_csv: rec=[%s]" % rec)

			if not rec :
				continue

			result3 = self.PATTERN3.match(rec)
			result4 = self.PATTERN4.match(rec)
			result5 = self.PATTERN5.match(rec)
			if result3 :
				function = result3.group(1)
				result = self.PATTERN6.match(function)
				if result :
					function = ""
			elif result4 :
				self.debug("gcov_csv: function=[%s] coverage=[%s] lines=[%s]" % (function, result4.group(1), result4.grouyp(2)))
				coverage = float(result4.group(1))
				lines = int(result4.group(2))
				if (function) :
					category = self.path_to_category(path)
					self.debug("gcov_csv: path=[%s] category=[%s]" % (path, category))
					val = lines * coverage / 100
					avail = int(val) + (val != int(val))
					print "%s\t%s\t%s\t%s\t%d\t%d" % ("\t".join(category), path, function, coverage, lines, avail)
					function = ""
			elif result5 :
				function = ""
			elif rec == "No executable line" :
				function = ""
			else :
				self.warning("gcov format. (rec=[%s])" % rec)

	def file_csv(self, path) :
		name = os.path.basename(file)
		comment = 0
		execs = 0
		lines = 0

		with open(path, "r") as wc :
			for s in wc :
				s = s.rstrip("\n")
				lines += 1
				if not comment :
					if self.PATTERN7.match(s) :
						self.debug("%s:%s:%s" % (name, lines, s))
						continue
					elif self.PATTERN10.match(s) :
						self.debug("%s:%s:%s" % (name, lines, s))
						continue
					elif self.PATTERN11.match(s) :
						self.debug("%s:%s:%s" % (name, lines, s))
						continue
					else :
						result8 = self.PATTERN8.match(s)
						if result8 :
							comment = 1
							if not result8.group(1) :
								self.debug("%s:%s:%s" % (name, lines, s))
								continue
				else :
					result9 = self.PATTERN9.match(s)
					if result9 :
						comment = 0
						if not result9.group(1) :
							self.debug("%s:%s:%s" % (name, lines, s))
							continue
					else :
						self.debug("%s:%s:%s" % (name, lines, s))
						continue
				execs += 1
		category = self.path_to_category(path)
		print "%s\t%s\t-\t0.0\t%d\t0" % ("\t".join(category), path, execs)

	def find_gcov(self, dir) :
		find = subprocess([ "find", dir, "-name", "*.gcno" ], stdout=subprocess.PIPE)
		for rec in iter(find.stdout.readline, b'') :
			gcno = rec.strip()
			path = gcno[0 : -5]
			gcda = path + ".gcda"
			file = ""
			for ext in [ ".c", ".cc", ".cpp" ] :
				if os.path.exists(path + ext) :
					file = path + ext
					break

			self.debug("find_gcno: gcno=[%s] gcda=[%s] file=[%s]" % (gcno, gcda, file))
			if os.patyh.exists(gcda) :
				if file == "" :
					file = path + ".o"
				self.gcov_csv(file)
			elif file :
				self.file_csv(file)

	def __init__(self, argv) :
		if len(argv) < 3 :
			print "usage: python %s projfile dir ..." % os.path.basename(argv[0])
			sys.exit(1)

		self.make_projlist(argv[1])
		print "システムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント\tファイルパス\t関数\tcoverage\t対象行数\t実行行数"
		for index in range(2, len(argv)) :
			self.find_gcno(argv[index])

if __name__ == "__main__" :
	find_gcov(sys.argv)
