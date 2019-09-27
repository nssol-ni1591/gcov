#!/usr/bin/python
# -*- coding: utf-8 -*-
# -*- vi: set ts=4 sts=4 sw=4 : -*-

import codecs
import os
import re
import subprocess
import sys

class category_component :

	category = []
	component = ""

	def __init__(self, category, component) :
		self.category = category
		self.component = component

	def category_to_string(self) :
		return " ".join(category)

	def equals_categories(self, category) :
		rc = category[0] == category[0] \
			and category[1] == category[1] \
			and category[2] == category[2] \
			and category[3] == category[3]
		return rc

	def get_component(self) :
		return self.component

	def get_category(self) :
		return self.category


class make_mapping :

	STATIC_MAPPING = {
		"..." : "...",
	}

	SKIP_COMPONENTS = [
		"...",
	]

	PATTERN1 = re.compile("^([\w\/]*)\/src\/(.*)$")
	PATTERN2 = re.compile("^([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\w\.-]*)\t*$")
	PATTERN3 = re.compile("^\S*\.(sh|adf|sts|ear|jar)$")

	def debug(self, msg) :
#		print msg
		return

	def warning(self, msg) :
		print >> sys.stderr, "Warning: %s" % msg
		return

	def skip_component(self, component) :
		result3 = self.PATTERN3.match(component)
		if result3 :
			self.debug("skip_component: excluded component=[%s]" % component)
			return True
		for c in self.SKIP_COMPONENTS :
			if c == component :
				self.debug("skip_component: excluded component=[%s]" % component)
				return True
		return False

	def check_duplicate_unit(selt, list, unit, component, category) :
		if list.get(unit) :
			if component == list[unit].get_component() :
				if not list[unit].equals_categories(category) :
					p1 = " ".join(category)
					p2 = list[unit].categories_to_string()
					self.warning("duplicate components. (component=[%s] category=[%s] => [%s)" % (component, p1, p2))
				return False
			if not list[unit].equals_categories(category) :
				p1 = list[unit].get_component()
				p2 = list[unit].categories_to_string()
				self.warning("different category in same unit. (unit=[%s] comp/cate=[%s]/[%s] => [%s]/[%s])" % (unit, component, " ".jpin(category), p1, p2))
			return False
		return True

	def check_parent_unit(selt, list, unit, component, category) :
		d = os.path.dirname(unit)
		while d != "" :
			u = list.get(d)
			if u:
#				self.debug("check_parent_unit:; unit=[%s] component=[%s] d=[%s] u=[%s]" % (unit, component, d, u))
				if not u.equals_categories(category) :
					p1 = " ".join(category)
					self.warning("parent unit exists. (componment=[%s] unit=[%s] d=[%s] category=[%s])" % (component, unit, d, p1))
				else :
#					self.debug("exists_parent_unit: skip unit=[%s] component=[%s] d=[%s]" % (unit, component, d))
					pass
				return False
			d = os.path.dirname(d)
		return True

	def check_child_unit(self, list, unit, component, category) :
		rc = True
		for u in list.keys() :
			if u.startswith(unit + "/") :
				if not list[u]/equals_categories(category) :
					p1 = " ".join(category)
					self.warning("child unit exists. (component=[%s] unit=[%s] child=[%s] categor=[%s])" % (component, unit, u, p1))
					rc = False
				self.debug("check_child_unit: delete component=[%s] unit=[%s] child=[%s (%s)])" % (component, unit, u, list[u].get_component()))
				del list[u]
		return rc

	def find_unit(self, dir, component) :
		unit = self.STATIC_MAPPING.get(component)
		if unit :
			if not os.path.exists(unit) :
				print("Error: unit not exists. (unit=[%s] component=[%s])" % (unit, dir + unit))
				sys.exit(1)
			return unit

		paths = []
		find = subprocess.Popen(["/cygdrive/c/cygwin/bin/find.exe", dir, "-name", component, "-type", "f"], stdout = subprocess.PIPE)
		for path in iter(find.stdout.readline, b'') :
			path = path.rstrip("\n")
			self.debug("find_unit: push [%s]" % path)
			paths.append(path[len(dir) : ])

		if not paths :
			return ""

		srcdirs = []
		if len(paths) > 1 :
			for path in paths :
				if  ("/bin/" in path) \
				 or ("/doc/" in path) \
				 or (component.startswith("lib") and component.endswith(".so") and "/lib/" in path) :
					pass
				else :
					srcdirs.append(path)
			if len(srcdirs) > 1 :
				self.warning("duplicate paths. (component=[%s] paths=[%s])" % (component, " ".join(srcdirs)))
			elif len(srcdirs) < 1 :
				srcdirs = paths
		else :
			srcdirs = paths

		name = component
		if component.startswith("lib") and component.endswith(".so") :
			name = component[3 : len(component) - 3]

		srcdir = os.path.dirname(srcdirs[0])
		unit = ""
		if name == os.path.basename(srcdir) :
			unit = srcdir
		elif name == "Dss" + os.path.basename(srcdir) :
			unit = srcdir
		else :
			index = srcdir.find("src")
			if index > 0 :
				unit = srcdir[0 : index - 1]
			else :
				unit = srcdir

		if not os.path.exists(dir + unit) :
			print("Error: unit not exists. (unit=[%s] path=[%s])" % (unit, dir + unit))
			sys.exit(1)

#		self.debug("find_unit: dir=[%s] component=[%s] unit=[%s]" % (dir, component, unit))
		return unit

	def make_complist(self, path, topdir) :
		list = {}

		with open(path, "r") as ins :
#		with open(path, "r", encoding="cp932") as ins :
			for rec in ins :
				rec = rec.strip()
#				self.debug("make_complist: rec=[%s]" % rec)

				category = []
				component = ""
				result2 = self.PATTERN2.match(rec)
				if result2 :
					category = (result2.group(1), result2.group(2), result2.group(3), result2.group(4))
					component = result2.group(5)
				else :
					self.warning("complist file format. (rec=[%s])" % rec)
					continue

				if not component or component == "0" or component == "-" :
					continue
				if self.skip_component(component) :
					continue

				unit = self.find_unit(topdir, component)
				if unit == "" :
					self.warning("component not found. (component=[%s] category=[%s])" % (component, " ".join(category)))
					continue

				if not self.check_duplicate_unit(list, unit, component, category) :
					continue
				if not self.check_parent_unit(list, unit, component, category) :
					continue
				if not self.check_child_unit(list, unit, component, category) :
					continue

				self.debug("make_complist: add unit=[%s] component=[%s] category=[%s]" % (unit, component, " ".join(category)))
				list[unit] = category_component(category, component)
		return list

	def __init__(self, argv) :
		if (len(argv) != 3) :
			print "usage: python %s complist dir" % os.path.basename(argv[0])
			sys.exit(1)

		path = argv[2]
		if path[-1] != "/" :
			path += "/"

		list = self.make_complist(argv[1], path)
		print u"コンパイル単位\tシステムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント"
		for unit in list :
			print "%s\t%s\t%s" % (unit, "\t".join(list[unit].get_category()), list[unit].get_component())

if __name__ == "__main__" :
#	sys.stdout = codecs.getwriter("utf-8")(sys.stdout)
	make_mapping(sys.argv)
