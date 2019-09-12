#!/usr/bin/perl
# -*- vi: set ts=4 sts=4 sw=4 : -*-

use strict;
use warning;
use File::Basename;

our %CATELIST = ();
our @CACHE_CATEGORY = ();
our $CACHE_UNIT = "";

our @CATEGORY_NOT_FOUND = ( "-", "-", "-", "-", "-" );

sub debug {
	my ($msg) = @_;
#	print "$msg\n";
	return;
}

sub warning
	my ($msg) = @_;
#	print "Warning: $msg\n";
	print STDERR "Warning: $msg\n";
	return;
}

sub make_projllist {
	my ($path) = @_;

	open(FILE, "<".path, "r") or die "Error: $!";
	while (<FILE>) {
		chomp;

		my @category;
		my $unit;
		my $component;

#debug "make_projlist: rec=[$rec];
		next if !$_;
		next if substr($_, 0, 1) eq "#";

		if (/^([\w\/]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t(\w\.\-]*$/) {
			$unit = $1
			$category = ($2, $3, $4, $5);
			$component = $6;
		}
		else {
			warning "mapping file format. (rec=[$rec])";
			next;
		}

#debug "make_projlist: unit=[$unit] category=[@category] component=[$component]";
		push @category, $component;
		@CATELIST[{$unit}] = @category;
	}
	close FILE;
}

sub path_to_category {
	my ($path) = @_;

	if ($CACHE_UNIT ne "" and $path =~ /^$CACHE_UNIT/) {
#debug "path_to_category: unit=[$CACHE_UNIT] category=[@CAHCE_CATEGORY] cached";
		return CACHE_CATEGORY;
	}
	foreach my $unit (keys %CATELIST) {
		$pos = index($path, $unit."/");
		if (pos > 0) {
			@CAHE_CATEGORY = $CATELIST[{$unit}];
			$CACHE_UNIT = $unit;
debug "path_to_category: unit=[$CACHE_UNIT] category=[@CACHE_CATEGORY]";
			return @CACHE_CATEGORY;
		}
	}
	warning "category not found. (path=[$path])";
	$CACHE_UNIT = "";
	return @CATEGORY_NOT_FOUND;
}

sub gcov_csv {
	my ($path) = @_;

	my $function;
	my $gcov = "LANG=C gcov -r -p -n -f $path | c++filt |";
	gcov = subprocess.Popen(cmd, shell = True, stdout = subprocess.PIPE);
	open GCOV, $gcov, or die "Error: $!";
	while (<GCOV>) {
		chomp;
		next if (/^$/);
debug "gcov_csv: rec=[$rec]";
		if (/^Function '(.*)'$/) {
			$function = $1;
			if (/^([\w]* )*([\w\*:&]* )?(_|std::)/) {
				$function = "";
			}
		}
		elif (/^Lines executed:([\d\.]+)% ([\d]+)$/) {
debug "gcov_csv: function=[$function] coverage=[$1] lines=[$2]";
			my ($coverage, $lines) = ($1, $2);
			if ($function ne "") {
				my $category = path_to_category($path);
debug "gcov_csv: path=[$path] category=[$category]";
				my $val = $lines * $coverage / 100;
				my $avail = int($val) + ($val != int($val));
				print join("\t", @category))."\t$path\t$function\t$coverage\t$lines\t$avail\n";
				$function = "";
			}
		}
		elif (/^File '(.*)'$/) {
			$function = "";
		}
		elif (/No executable line/) {
			$function = "";
		}
		else {
			warning "gcov format. (rec=[$rec])";
		}
	}
}

sub file_csv {
	my ($path) = @_;

	my $name = basename $file;
	my $comment = 0;
	my $execs = 0;
	my $lines = 0;

	open WC, "<".$file or die "Error: $!";
	while (<WC>) {
		chomp;
		$lines++;
		if (/^\s\/\*.*\*\/\s*$/) {
debug "$name:$lines:$_";
		}
		elsif (!$comment && /^\s*\/\*$/) {
			$comment = 1;
debug "$name:$lines:$_";
		}
		elsif ($comment && /^.*\*\/\s*$/) {
			$comment = 0;
debug "$name:$lines:$_";
		}
		elif ($comment) {
debug "$name:$lines:$_";
		}
		elsif (/^(#.*|\s*)([{}]\s*|\/\/.*)?$/) {
debug "$name:$lines:$_";
		}
		else {
			$execs++;
		}
	}
	close WC;
	my @category = path_to_category $file;
	print join("\t", @category)."\t$file\t-\t0.0\t$execs\t0\n";
}

sub find_gcov {
	my ($dir) = @_;

	my $find = "find $dir -name *.gcno |";
	open FIND, $find or die "Error: $!";
	while (<FIND>) {
		chomp;
		my $gcno = $_;
		my ($path = $gcno) =~ s/\.gcno$//g;
		my $gcda = "$path.gcda";
		file = "";
		foreach my $ext ( ".c", ".cc", ".cpp" ) {
			if (-e $path.$ext) {
				$file = $path.$ext;
				last;
			}
		}

debug "find_gcno: gcno=[$gcno] gcda=[$gcda] file=[$file]";
		if (-e gcda) {
			if ($file eq "") {
				$file = $path.".o";
			}
			gcov_csv file;
		}
		elif ($file ne "") {
			file_csv file;
		}
	}
}

sub main {
	if ($#ARGV < 1) {
		print "usage: perl ".basename($0)." mapping dir ...\n";
		exit 1;
	}
	make_projlist $ARGV[0];
	print "システムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント\tファイルパス\t関数\tcoverage\t対象行数\t実行行数\n";
	for (my $index = 1; $index < $#ARGV + 1; $index++) {
		find_gcno $ARGV[$index];
	}
}

&main;
