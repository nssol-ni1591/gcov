#!/usr/bin/perl
# -*- vi: set ts=4 sts=4 sw=4 : -*-

use strict;
#use warning;
use utf8;
use File::Basename;

#binmode STDOUT ":utf8";	# why?
#binmode STDERR ":utf8";	# why?

our %STATIC_MAPPING = (
	"..." => "...",
);

our @SKIP_COMPONENTS = (
	"...",
);

sub debug {
	my ($msg) = @_;
	print "$msg\n";
	return;
}

sub warning {
	my ($msg) = @_;
	print STDERR "Warning: $msg\n";
#	print "Warning: $msg\n";
	return;
}

sub categories_to_string {
	my ($c) = @_;
	return @$c[0]." ".@$c[1]." ".@$c[2]." ".@$c[3];
}

sub equals_categories {
	my ($c1, $c2) = @_;
	my $rc = @$c1[0] eq @$c2[0]
		&& @$c1[1] eq @$c2[1]	# "and" ではwarning...why?
		&& @$c1[2] eq @$c2[2]
		&& @$c1[3] eq @$c2[3]
		;
	return$rc;
}

sub get_component {
	my ($c) = @_;
	return @$c[4];
}

sub set_component {
	my ($category, $c) = @_;
	push @$category, $c;
}

sub skip_component {
	my ($c) = @_;

	if ($c =~ /\.(sh|adf|sts|ear|jar)$/) {
debug("skip_compoent: excluded component=[$c]");
		return 1;
	}
	for (@SKIP_COMPONENTS) {
		if ($_ eq $c) {
debug("skip_compoent: excluded component=[$c]");
			return 1;
		}
	}
	return 0;
}

sub check_duplicate_unit {
	my ($list2, $unit, $component, $cate) = @_;
	my %list = %$list2;
	my @category = @$cate;

	if ($list{$unit}) {
		if ($component eq get_component(\@{$list{$unit}})) {
			if (!equals_categories(\@category, \@{$list{$unit}})) {
				my $p1 = categories_to_string(\@category);
				my $p2 = categories_to_string(\@{$list{$unit}});
				warning("duplicate components. (component=[$component] category=[$p1] => [$p2])");
			}
			return 0;
		}
		if (!equals_categories(\@category, \@{$list{$unit}})) {
			my $p1 = get_component(\@{$list{$unit}});
			my $p2 = categories_to_string(\@{$list{$unit}});
			warning("different category in same unit. (unit=[$unit] comp/cate=[$component]/[@category] => [$p1]/[$p2])");
		}
		return 0;
	}
	return 1;
}

sub check_parent_unit {
	my ($list2, $unit, $component, $cate) = @_;
	my %list = %$list2;
	my @category = @$cate;

	my $d = $unit;
	while ($d ne ".") {
		$d = dirname $d;
		if ($list{$d}) {
			if (!equals_categories(\@category, \@{$list{$d}})) {
				my $p1 = categories_to_string(\@category);
				warning("parent unit exists. (component=[$component] unit=[$unit] parent=[$d] categopry=[$p1])");
			}
			else {
#debug "check_parent_unit: skip unit=[$unit] component=[$component] parent=[$d]";
			}
			return 0;
		}
	}
	return 1;
}

sub check_child_unit {
	my ($list2, $unit, $component, $cate) = @_;
	my %list = %$list2;
	my @category = @$cate;

	my $rc = 1;
	for (keys %list) {
		if (/^$unit\//) {
#debug "exists_child_unit: _=[$_] unit=[$unit] category=[".categories_to_string \@[$list{$_}]."]";
			if (!equals_categories(\@category, \@{list{$_}})) {
				my $p1 = categories_to_string(\@category);
				warning("child unit exists. (component=[$component] unit=[$unit] child=[$_] category=[$p1]");
				$rc = 0;
			}
debug("check_child_unit: delete componet=[$component] unit=[$unit] child=[$_ (".(get_component(\@{$list{$_}})).")]");
			delete $list2->{$_};
		}
	}
	return $rc;
}

sub find_unit {
	my ($dir, $component) = @_;

	my $unit = $STATIC_MAPPING{$component};
	if ($unit) {
		if (!-d $dir.$unit) {
			die "Error: unit not exists. (unit=[$unit] path=[".$dir.$unit."])\n";
		}
		return $unit;
	}

	my @paths = ();
	my $find = "find $dir -name $component -type f |";
	open FIND, $find or die "Error: $!";
	while (<FIND>) {
		chomp;
#debug "find_unit: push [$_]";
		$_ =~ s/^$dir//g;
		push @paths, $_;
	}
	close FIND;

	if (!@paths) {
		return "";
	}

	my @srcdirs = ();
	if ($#paths >= 0) {
		foreach (@paths) {
			if ((/\/(bin|doc)\//) or (/\/lib\// and $component =~ /^lib.*\.so$/)) {
			}
			else {
				push @srcdirs, $_;
			}
		}
		if ($#srcdirs > 0) {
			warning("duplicate files. (component=[$component] srcdirs=[@srcdirs])");
		}
		elsif ($#srcdirs < 0) {
			@srcdirs = @paths;
		}
	}

	my $name = $component;
	if ($component =~ /lib(\w*)\.so/) {
		$name = $1;
	}

	my $srcdir = dirname $srcdirs[0];
	my $unit = "";
	if ($name eq basename $srcdir) {
		$unit = $srcdir;
	}
	elsif ($name eq "Dss".basename $srcdir) {
		$unit = $srcdir;
	}
	else {
		my $ix = index $srcdir, "src";
		if ($ix > 0) {
			$unit = substr $srcdir, 0, $ix - 1;
		}
		else {
			$unit = $srcdir;
		}
	}

debug("find_unit: return unit=[$unit] component=[$component] srcdir=[$srcdir]");
	if (!-d $dir.$unit) {
		die "Error: unit not exists. (unit=[$unit])\n";
	}
	return $unit;
}

sub make_complist {
	my ($path, $topdir) = @_;
	my %list = ();

	open FILE, "<".$path or die "Error: $!";
	binmode FILE, ":crlf:encoding(cp932)";
	while (<FILE>) {
		chomp;
		my @category = ();
		my $component;
#debug "make_complist: rec=[$_]";
		if (/^([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\S ]*)\t([\w\.-]*)\t*$/) {
			@category = ($1, $2, $3. $4);
			$component = $5;
		}
		else {
			warning("complist file format. (rec=[$_])");
			next;
		}
		next if (!$component or $component eq "0" or $component eq "-");
		next if (skip_component $component);

		my $unit = find_unit $topdir, $component;
		if (!$unit) {
			warning("component not found. (component=[$component] category=[@category])");
			next;
		}
		next if (!check_duplicate_unit(\%list, $unit, $component, \@category));
		next if (!check_parent_unit(\%list, $unit, $component, \@category));
		next if (!check_child_unit(\%list, $unit, $component, \@category));

debug("make_complist: add unit=[$unit] component=[$component] category=[@category]");
		set_component(\@category, $component);
		@{$list{$unit}} = @category;
	}
	close FILE;
	return %list;
}

sub main() {
	if ($#ARGV < 1) {
		print "usage: perl ".basename $0." complist dir\n";
		exit 1;
	}

	my $path = $ARGV[1];
	if (substr($path, -1) ne "/") {
		$path = $path."/";
	}
	my %list = make_complist $ARGV[1], $path;
	print "コンパイル単位\tシステムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント\n";
	foreach (keys %list) {
		print $_."\t".join("\t", @{$list{$_}})."\n";
	}
}

&main;
