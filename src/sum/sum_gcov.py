#!/usr/bin/python
# -*- coding: utf-8 -*-
# -*- vi: set ts=4 sts=4 sw=4 : -*-

import codecs
import os
import re
import sys

class sum_gcov :

	MODE = ""

	def debug(self, msg) :
#		print msg
		return

	def warning(self, msg) :
		print >> sys.stderr, "Warning: %s" % msg
		return

	def print_sum(self, system, subsystem, group, service, lines, execs) :
		if self.MODE == "system" :
			subsystem = "-"
			group = "-"
			service = "-"
		elif self.MODE == "subsystem" :
			group = "-"
			service = "-"
		elif self.MODE == "group" :
			service = "-"

		self.debug("print_sum: system=[%s] subsystem=[%s] group=[%s] service=[%s] lines=[%d] execs=[%d]"
			% (system, subsystem, group, service , lines, execs))
		if lines == 0 :
			coverage = 0.0
		else :
			coverage = float(execs) / float(lines)

		print "%s\t%s\t%s\t%s\t%d\t%d\t%f" % (system, subsystem, group, service, lines, execs, coverage)

	def sum_tsv(self, path) :
		sum_system = ""
		sum_subsystem = ""
		sum_group = ""
		sum_service = ""
		sum_lines = 0
		sum_execs = 0

#		pattern = re.compile("^([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\w\.\-]*)\t([\w\.\/]*)\t([\S ]*)\t([\d\.]*)\t(\d*)\t(\d*)$")
		pattern = re.compile("^([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\w\.\-]*)\t([\S]*)\t([\S ]*)\t([\d\.]*)\t(\d*)\t(\d*)$")
		with open(path, "r") as INS :
#		with cocecs.open(path, "r", encoding="cp932") as INS :
			INS.readline()
			for rec in INS :
				rec = rec.strip()
#				self.debug("sum_tsv: rec=[%s]" % rec)
				if not rec or rec.startswith("#") :
					continue

				result = pattern.match(rec)
				if result :
					system = result.group(1)
					subsystem = result.group(3)
					group = result.group(3)
					service = result.group(4)
					lines = int(result.group(9))
					execs = int(result.group(10))

					self.debug("sum_tsv: system=[%s] subsystem=[%s] group=[%s] service=[%s] lines=[%s] execs=[%s]"
						% (system, subsystem, group, service, lines, execs))

					if self.MODE == "system" and system == sum_system \
					 or self.MODE == "subsystem" and subsystem == sum_subsystem \
					 or self.MODE == "group" and group == sum_group \
					 or self.MODE == "service" and service == sum_service \
					:
						sum_lines += lines
						sum_execs += execs
					else :
						if not system :
							print_sum(sum_system, sum_subsystem, sum_group, sum_service, sum_lines, sum_execs)

						sum_system = system
						sum_subsystem = subsystem
						sum_group = group
						sum_service = service
						sum_lines = lines
						sum_execs = execs
				else:
					self.warning("tsv format. (rec=[%s])" % rec)
		INS.close
		self.print_sum(sum_system, sum_subsystem, sum_group, sum_service, sum_lines, sum_execs)

	def __init__(self, argv) :
		if ((len(argv) < 3)
		 or (argv[1] != "service" and argv[1] != "group" and argv[1] != "subsystem" and argv[1] != "system")) :
			print "usage: python %s [service|group|subsystem|system] gcov.tsv ..." % os.path.basename(argv[0])
			sys.exit(1)

		self.MODE = argv[1]
		print "システムブロック\tサブシステム\tサービス群\tサービス\t対象行数\t実行行数\カバレッジ"
		for ix in range(2, len(argv)) :
			self.sum_tsv(argv[ix])

if __name__ == "__main__" :
	sum_gcov(sys.argv)
