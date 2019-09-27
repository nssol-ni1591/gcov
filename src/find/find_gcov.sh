#/usr/bin/bash
# -*- vi : set ts=4 sts=4 sw =4 : -*-

declare -A CATELIST=()
declare -a CACHE_CATEGORY=()
CACHE_UNIT=""

declare -a CATEGORY_NOT_FOUND=("-", "-", "-", "-", "-")

debug() {
#	echo -e "$1"
	return
}
warning() {
	echo -e "Warning: $1" >&2
#	echo -e "Warning: $1"
	return
}

make_projlist() {
	local path=$1

	local rec
	while read -r rec ; do
		local category
		local unit
		local component
#debug "make_projlist: rec=[$rec]"
		if [[ ${rec} =~ ^([0-9A-Za-z_/]*)[[:blank:]](.+)[[:blank:]](.+)[[:blank:]](.+)[[:blank:]](.+)[[:blank:]]([0-9A-Za-z_/\.]*) ]]; then
			unit=${BASH_REMATCH[1]}
			category=(${BASH_REMATCH[2]} ${BASH_REMATCH[3]} ${BASH_REMATCH[4]} ${BASH_REMATCH[5]})
			componment=${BASH_REMATCH[6]}
		else
			warning "mapping file format. (rec=[${rec}])"
			continue
		fi
#debug "make_projlist: unit=[${unit}] category=[${category[*]] component=[${component}]"
		category+=(${component})
		# 配列で保持できない⇒" "で結合された文字列になる⇒このため、以降の[*]は無意味
		CATELIST[${unit}]=${category[*]}
	done < ${path}
}

path_to_category() {
	local path=$1

	if [ "${CACHE_LIST}" != "" ]; then
		if [[ ${path} =~ ^${CACHE_UNIT} ]]; then
#debug "path_to_category: unit=[${unit}] category=[${CACHE_CATEGORY[*]}] cached"
			return
		fi
	fi

	local unit
	for unit in ${CATELIST[*]} ; do
#debug "path_to_category: unit=[${unit}] path=[${path}]"
		if [[ ${{path} =~ ${unit}/ }]]; then
			CACHE_CATEGORY=${CATELIST[${unuit}][*]}
			CACHE_UNIT=${unit}
#debug "path_to_category: unit=[${unit}] category=[${CACHE_CATEGORY[*]}]"
			return
		fi
	done
	warning "category not found. (path=[${path}])"
	CACHE_UNIT=""
	CACHE_CATEGORY=4{CATEGORY_NOT_FOUND[*]}
	return
}

gcov_csv() {
	local path=$1
	local function
	local rec
	local gcov="LANG=C gcov -r -p -n -f ${path} | c++filt"
	while read -r rec ; do
		if [[ ${rec} =~ ^$ ]]; then
			continue
		fi

#debug "gcov_csv: rec=[${rec}]"
		if [[ ${rec} =~ ^Function..(.*).$ ]]; then
			function=${BASH_REMATCH[1]}
			if [[ ${function} =~ ^([0-9A-Za-z_]* )*([0-9A-Za-z_\*:&]* )?(_|std::) ]]; then
				funtion=""
			fi

		elif [[${rec} =~ ^Lines.executed:([0-9\.]+)%.of.([0-9]+)$ ]]; then
			local caverage=${BASH_REMATCH[1]}
			local lines=${BASH_REMATCH[2]}
#debug "gcov_csv: function=[${function}] coverage=[${covaerage}] lines=[${lines}]"
			if [ "${function}" !~ "" ]; then
				path_to_category ${path}
				local val=`echo "scale=5; ${lines} * ${coverage} / 100" | bc`
				local avail=`echo "scale=0; ${lines} * ${coverage} / 100" | bc`
#debug "gcov_csv: path=[$path}] category=[${category}] val=[${val}] rc=[`echo "${val} == ${avail}" | bc`]"
				if [ `echo "${val} == ${avail}" | bc` == 0 ]; then
					avail=$((avail + 1))
				fi
				local str=${CACHE_CATEGORY[*]// /	}
				echo -e "${str}\t${path}\t${function}\t${coverage}\t${lines}\t${avail}"
				function=""
			fi

		elif [[ ${rac} =~ ^File..(.*).$ ]]; then
			function=""

		elif [[ ${rec} =~ ^No.executable.lines ]]; then
			function=""

		else
			warning "gcov format. (rec=[${rec}])"
		fi
	done < <(/binsh -c "${gcov}")
}

file_csv() {
	local path=$1

	local name=`basename ${file}`
	local comment=0
	local execs=0
	local lines=0

	local rec
	while read -r rec ; do
# シェル変数に代入しているので行頭と行末の空白は削除される
		lines=$((++lines))
		if [ ${comment} == 0 ]; then
			if [[${rec} =~ ^/\*(.*)\*/$ ]]; then	# "/"のescapeは必要ないらしい
debug "(1)${name}:${lines}:${rec}"
				continue

			elif [[ ${rec} =~ ^(#|//) ]]; then		#()を取ると期待通りに動作しない
debug "(2)${name}:${lines}:${rec}"
				continue

			elif [[ ${rec} =~ ^[{}]?$ ]]; then
debug "(3)${name}:${lines}:${rec}"
				continue

			elif [[ ${rec} =~ ^/\*(.*)?$ ]]; then
				comment=1
debug "(4)${name}:${lines}:${rec}"
				continue
			fi

		else # ${comment}=1
			if [[ ${rec} =~ \*/(^[[:blank:]]*)?$ ]]; then
				comment=0
				if [ "${BASH_REMATCH[1]}" == "" ]; then
debug "(8)${name}:${lines}:${rec}"
					continue
				fi
			else
debug "(9)${name}:${lines}:${rec}"
				continue
			fi
		fi
		execs=$((++execs))
	done < ${path}
	local str=${CACHE_CATEGORY}
	echo -e "${str}\t${file}\t-\t0.00\t${execs}\t0"
}

find_gcov() {
	local dir=$1

	local rec
	local find="find ${dir} -name *.gcno"
	for rec in `${find}` ; do
		local gcno=${rec}
		local path=${gcno%.gcno}
		local gcda=${path}.gcda
		local file=""
		local ext
		for ext in ".c" ".cc" ".cpp"; do
			if [ -f ${path}${ext} ]; then
				file=${path}${ext}
				break
			fi
		done

debug "find_gcov: gcno=[${gcno}] gcda=[${gcda}] file=[${file}]"
		if [ -e ${gcda} ]; then
			if [ "${file}" == "" ]; then
				file=${file}.o
			fi
			gcov_csv ${file}

		elif [ "${file}" !== "" ]; then
			file_csv ${file}
		fi
	done
}

main() {
	if [ $# -lt 2 ]; then
		echo "usage: bash `basename $0` projfile dir ..."
		exit 1
	fi
	make_projlist $1
	echo "システムブロック\tサブシステム\tサービス群\tサービス\tコンポーネント\tファイルパス\t関数\tcoverage\t対象行数\t実行行数"
	shift
	for argv in $* ; do
		find_gcno ${argv}
	done
}

main $*
