#!/usr/bin/env python3

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import yaml
import os
import sys
from html.parser import HTMLParser
import argparse


class DependencyReportParser(HTMLParser):
    # This class parses the given html file to find all dependency reports under "Project dependencies"
    # and "Projection transparent dependencies" sections.
    # The parser works based on the state machine and its state is updated whenever it reads a new tag.
    # The state changes as below:
    #
    # none -> h2_start -> project_dependencies_start -> h3_start -> compile_start -> table_start -> row_start -> th_start / td_start -> th_end / td_end -> row_end -> table_end -> compile_end -> h3_end -> project_dependencies_end -> h2_end -> none

    attr_index = 0
    group_id = None
    artifact_id = None
    version = None
    classifier = None
    dep_type = None
    license = None
    state = "none"
    dep_to_license = None
    compatible_license_names = None
    include_classifier = False
    druid_module_name = None

    def __init__(self, druid_module_name, compatible_license_names):
        HTMLParser.__init__(self)
        self.state = "none"
        self.druid_module_name = druid_module_name
        self.compatible_license_names = compatible_license_names

    def parse(self, f):
        self.dep_to_license = {}
        self.feed(f.read())
        return self.dep_to_license

    def handle_starttag(self, tag, attrs):
        # print("current: {}, start tag: {}, attrs:{} ".format(self.state, tag, attrs))
        if self.state == "none":
            if tag == "h2":
                self.state = "h2_start"
        
        if self.state == "h2_start":
            if tag == "a":
                for attr in attrs:
                    if attr[0] == "name" and (attr[1] == "Project_Dependencies" or attr[1] == "Project_Transitive_Dependencies"):
                        self.state = "project_dependencies_start"
                        self.include_classifier = False

        if self.state == "h2_end":
            if tag == "h3":
                self.state = "h3_start"
        
        if self.state == "h3_start":
            if tag == "a":
                for attr in attrs:
                    if attr[0] == "name" and attr[1] == "compile":
                        self.state = "compile_start"
            
        if self.state == "h3_end":
            if tag == "table":
                self.state = "table_start"

        if self.state == "table_start":
            if tag == "tr":
                self.state = "row_start"
                self.clear_attr()

        if self.state == "row_end":
            if tag == "tr":
                self.state = "row_start"
                self.clear_attr()
        
        if self.state == "row_start":
            if tag == "td":
                self.state = "td_start"
            elif tag == "th":
                self.state = "th_start"

        if self.state == "th_end":
            if tag == "th":
                self.state = "th_start"
        
        if self.state == "td_end":
            if tag == "td":
                self.state = "td_start"

    def handle_endtag(self, tag):
        # print("current: {}, end tag: {}".format(self.state, tag))
        if self.state == "project_dependencies_start":
            if tag == "a":
                self.state = "project_dependencies_end"

        if self.state == "h2_start":
            if tag == "h2":
                self.state = "h2_end"
        
        if self.state == "project_dependencies_end":
            if tag == "h2":
                self.state = "h2_end"
        
        if self.state == "compile_start":
            if tag == "a":
                self.state = "compile_end"

        if self.state == "compile_end":
            if tag == "h3":
                self.state = "h3_end"
        
        if self.state == "table_start":
            if tag == "table":
                self.state = "none"

        if self.state == "td_start":
            if tag == "td":
                self.state = "td_end"
                self.attr_index = self.attr_index + 1
        
        if self.state == "th_start":
            if tag == "th":
                self.state = "th_end"

        if self.state == "row_start":
            if tag == "tr":
                self.state = "row_end"

        if self.state == "th_end":
            if tag == "tr":
                self.state = "row_end"
        
        if self.state == "td_end":
            if tag == "tr":
                self.state = "row_end"
                # print(json.dumps({"groupId": self.group_id, "artifactId": self.artifact_id, "version": self.version, "classifier": self.classifier, "type": self.dep_type, "license": self.license}))
                if self.group_id.find("org.apache.druid") < 0:
                    self.dep_to_license[get_dep_key(self.group_id, self.artifact_id, self.version)] = (self.license, self.druid_module_name)
        
        if self.state == "row_end":
            if tag == "table":
                self.state = "none"

    def handle_data(self, data):
        if self.state == "td_start":
            self.set_attr(data)
        elif self.state == "th_start":
            if data.lower() == "classifier":
                self.include_classifier = True

    def clear_attr(self):
        self.group_id = None
        self.artifact_id = None
        self.version = None
        self.classifier = None
        self.dep_type = None
        self.license = None
        self.attr_index = 0

    def set_attr(self, data):
        #print("set data: {}".format(data))
        if self.attr_index == 0:
            self.group_id = data
        elif self.attr_index == 1:
            self.artifact_id = data
        elif self.attr_index == 2:
            self.version = get_version_string(data)
        elif self.attr_index == 3:
            if self.include_classifier:
                self.classifier = data
            else:
                self.dep_type = data
        elif self.attr_index == 4:
            if self.include_classifier:
                self.dep_type = data
            else:
                self.set_license(data)
        elif self.attr_index == 5:
            if self.include_classifier:
                self.set_license(data)
            else:
                raise Exception("Unknown attr_index [{}]".format(self.attr_index))
        else:
            raise Exception("Unknown attr_index [{}]".format(self.attr_index))

    def set_license(self, data):
        if data.upper().find("GPL") < 0:
            if self.license != 'Apache License version 2.0':
                self.license = self.compatible_license_names[data]


outfile = None

def get_dep_key(group_id, artifact_id, version):
    return (group_id, artifact_id, version)


def build_compatible_license_names():
    compatible_licenses = {}
    compatible_licenses['Apache License, Version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['The Apache Software License, Version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache 2'] = 'Apache License version 2.0'
    compatible_licenses['Apache License 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache Software License - Version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['The Apache License, Version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache License version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache License Version 2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache License Version 2'] = 'Apache License version 2.0'
    compatible_licenses['Apache License v2.0'] = 'Apache License version 2.0'
    compatible_licenses['Apache License, version 2.0'] = 'Apache License version 2.0'

    compatible_licenses['Public Domain'] = 'Public Domain'

    compatible_licenses['BSD-2-Clause License'] = 'BSD-2-Clause License'

    compatible_licenses['BSD-3-Clause License'] = 'BSD-3-Clause License'
    compatible_licenses['New BSD license'] = 'BSD-3-Clause License'
    compatible_licenses['BSD'] = 'BSD-3-Clause License'
    compatible_licenses['The BSD License'] = 'BSD-3-Clause License'
    compatible_licenses['BSD licence'] = 'BSD-3-Clause License'
    compatible_licenses['BSD License'] = 'BSD-3-Clause License'
    compatible_licenses['BSD-like'] = 'BSD-3-Clause License'
    compatible_licenses['The BSD 3-Clause License'] = 'BSD-3-Clause License'
    compatible_licenses['Revised BSD'] = 'BSD-3-Clause License'
    compatible_licenses['New BSD License'] = 'BSD-3-Clause License'

    compatible_licenses['ICU License'] = 'ICU License'

    compatible_licenses['SIL Open Font License 1.1'] = 'SIL Open Font License 1.1'

    compatible_licenses['CDDL 1.1'] = 'CDDL 1.1'
    compatible_licenses['CDDL/GPLv2+CE'] = 'CDDL 1.1'
    compatible_licenses['CDDL + GPLv2 with classpath exception'] = 'CDDL 1.1'
    compatible_licenses['CDDL License'] = 'CDDL 1.1'

    compatible_licenses['Eclipse Public License 1.0'] = 'Eclipse Public License 1.0'
    compatible_licenses['The Eclipse Public License, Version 1.0'] = 'Eclipse Public License 1.0'
    compatible_licenses['Eclipse Public License - Version 1.0'] = 'Eclipse Public License 1.0'
    compatible_licenses['Eclipse Public License, Version 1.0'] = 'Eclipse Public License 1.0'

    compatible_licenses['Mozilla Public License Version 2.0'] = 'Mozilla Public License Version 2.0'
    compatible_licenses['Mozilla Public License, Version 2.0'] = 'Mozilla Public License Version 2.0'

    compatible_licenses['Creative Commons Attribution 2.5'] = 'Creative Commons Attribution 2.5'

    compatible_licenses['Creative Commons CC0'] = 'Creative Commons CC0'
    compatible_licenses['CC0'] = 'Creative Commons CC0'

    compatible_licenses['The MIT License'] = 'MIT License'
    compatible_licenses['MIT License'] = 'MIT License'

    compatible_licenses['-'] = '-'
    return compatible_licenses


def module_to_upper(module):
    extensions_offset = module.lower().find("extensions")
    if extensions_offset < 0:
        return module.upper()
    elif extensions_offset == 0:
        return module[0:len("extensions")].upper() + module[len("extensions"):len(module)]
    else:
        raise Exception("Expected extensions at 0, but {}".format(extensions_offset))


def print_outfile(string):
    print(string, file=outfile)


def print_error(string):
    print(string, file=sys.stderr)


def get_version_string(version):
    if type(version) == str:
        return version
    else:
        return str(version)


def print_license_phrase(license_phrase):
    remaining = license_phrase
    while len(remaining) > 0:
        # print("remaining: {}".format(remaining))
        # print("len: {}".format(len(remaining)))
        if len(remaining) > 120:
            chars_of_200 = remaining[0:120]
            phrase_len = chars_of_200.rfind(" ")
            if phrase_len < 0:
                raise Exception("Can't find whitespace in {}".format(chars_of_200))
            print_outfile("    {}".format(remaining[0:phrase_len]))
            remaining = remaining[phrase_len:]
        else:
            print_outfile("    {}".format(remaining))
            remaining = ""


def is_non_empty(dic, key):
    if key in dic and dic[key] is not None:
        if type(dic[key]) == str:
            return len(dic[key]) > 0
        else:
            return True
    else:
        return False


def print_license(license):
    license_phrase = "This product"
    if license['license_category'] == "source":
        license_phrase += " contains"
    elif license['license_category'] == "binary":
        license_phrase += " bundles"
    license_phrase += " {}".format(license['name'])
    if is_non_empty(license, 'version'):
        license_phrase += " version {}".format(license['version'])
    if is_non_empty(license, 'copyright'):
        license_phrase += ", copyright {}".format(license['copyright'])
    if is_non_empty(license, 'additional_copyright_statement'):
        license_phrase += ", {}".format(license['additional_copyright_statement'])
    if license['license_name'] != 'Apache License version 2.0':
        license_phrase += " which is available under {}".format(license['license_name'])
    if is_non_empty(license, 'additional_license_statement'):
        license_phrase += ", {}".format(license['additional_license_statement'])
    if is_non_empty(license, 'license_file_path'):
        license_file_list = []
        if type(license['license_file_path']) == list:
            license_file_list.extend(license['license_file_path'])
        else:
            license_file_list.append(license['license_file_path'])
        if len(license_file_list) == 1:
            license_phrase += ". For details, see {}".format(license_file_list[0])
        else:
            license_phrase += ". For details, "
            for each_file in license_file_list:
                if each_file == license_file_list[-1]:
                    license_phrase += ", and {}".format(each_file)
                elif each_file == license_file_list[0]:
                    license_phrase += "see {}".format(each_file)
                else:
                    license_phrase += ", {}".format(each_file)
    
    license_phrase += "."

    print_license_phrase(license_phrase)

    if 'source_paths' in license:
        for source_path in license['source_paths']:
            if type(source_path) is dict:
                for class_name, path in source_path.items():
                    print_outfile("      {}:".format(class_name))
                    print_outfile("      * {}".format(path))
            else:
                print_outfile("      * {}".format(source_path))

    if 'libraries' in license:
        for library in license['libraries']:
            if type(library) is not dict:
                raise Exception("Expected dict but got {}[{}]".format(type(library), library))
            if len(library) > 1:
                raise Exception("Expected 1 groupId and artifactId, but got [{}]".format(library))
            for group_id, artifact_id in library.items():
                print_outfile("      * {}:{}".format(group_id, artifact_id))


def find_druid_module_name(dirpath):
    ext_start = dirpath.find("/ext/")
    if ext_start > 0:
        # Found an extension
        subpath = dirpath[(len("/ext/") + ext_start):]
        ext_name_end = subpath.find("/")
        if ext_name_end < 0:
            raise Exception("Can't determine extension name from [{}]".format(dirpath))
        else:
            return subpath[0:ext_name_end]
    else:
        # Druid core
        return "core"


def check_licenses(license_yaml, dependency_reports_root):
    # Build a dictionary to facilitate comparing reported licenses and registered ones.
    # These dictionaries are the mapping of (group_id, artifact_id, version) to license_name.
    
    # Build reported license dictionary.
    reported_dep_to_licenses = {}
    compatible_license_names = build_compatible_license_names()
    for dirpath, dirnames, filenames in os.walk(dependency_reports_root):
        for filename in filenames:
            if filename == "dependencies.html":
                full_path = os.path.join(dirpath, filename)
                # Determine if it's druid core or an extension
                druid_module_name = find_druid_module_name(dirpath)
                print_error("Parsing {}".format(full_path))
                with open(full_path) as report_file:
                    parser = DependencyReportParser(druid_module_name, compatible_license_names)
                    reported_dep_to_licenses.update(parser.parse(report_file))

    if len(reported_dep_to_licenses) == 0:
        raise Exception("No dependency reports are found")

    print_error("Found {} reported licenses\n".format(len(reported_dep_to_licenses)))

    # Build registered license dictionary.
    registered_dep_to_licenses = {}
    skipping_licenses = {}
    with open(license_yaml) as registry_file:
        licenses_list = list(yaml.load_all(registry_file))
    for license in licenses_list:
        if 'libraries' in license:
            for library in license['libraries']:
                if type(library) is not dict:
                    raise Exception("Expected dict but got {}[{}]".format(type(library), library))
                if len(library) > 1:
                    raise Exception("Expected 1 groupId and artifactId, but got [{}]".format(library))
                for group_id, artifact_id in library.items():
                    if 'version' not in license:
                        raise Exception("version is missing in {}".format(license))
                    if 'license_name' not in license:
                        raise Exception("name is missing in {}".format(license))
                    if 'skip_dependency_report_check' in license and license['skip_dependency_report_check']:
                        if 'version' not in license:
                            version = "-"
                        else:
                            version = get_version_string(license['version'])
                        skipping_licenses[get_dep_key(group_id, artifact_id, version)] = license
                    else:
                        registered_dep_to_licenses[get_dep_key(group_id, artifact_id, get_version_string(license['version']))] = compatible_license_names[license['license_name']]

    if len(registered_dep_to_licenses) == 0:
        raise Exception("No registered licenses are found")
    
    # Compare licenses in registry and those in dependency reports.
    mismatched_licenses = []
    missing_licenses = []
    unchecked_licenses = []
    # Iterate through registered licenses and check if its license is same with the reported one.
    for key, registered_license in registered_dep_to_licenses.items():
        if key in reported_dep_to_licenses: # key is (group_id, artifact_id, version)
            reported_license_druid_module = reported_dep_to_licenses[key]
            reported_license = reported_license_druid_module[0]
            druid_module = reported_license_druid_module[1]
            if reported_license is not None and reported_license != "-" and reported_license != registered_license:
                group_id = key[0]
                artifact_id = key[1]
                version = key[2]
                mismatched_licenses.append((druid_module, group_id, artifact_id, version, reported_license, registered_license))
    
    # If we find any mismatched license, stop immediately.
    if len(mismatched_licenses) > 0:
        print_error("Error: found {} mismatches between reported licenses and registered licenses".format(len(mismatched_licenses)))
        for mismatched_license in mismatched_licenses:
            print_error("druid_module: {}, groupId: {}, artifactId: {}, version: {}, reported_license: {}, registered_license: {}".format(mismatched_license[0], mismatched_license[1], mismatched_license[2], mismatched_license[3], mismatched_license[4], mismatched_license[5]))
        print_error("")
    
    # Let's find missing licenses, which are reported but missing in the registry.
    for key, reported_license_druid_module in reported_dep_to_licenses.items():
        if reported_license_druid_module[0] != "-" and key not in registered_dep_to_licenses and key not in skipping_licenses:
            missing_licenses.append((reported_license_druid_module[1], key[0], key[1], key[2], reported_license_druid_module[0]))

    if len(missing_licenses) > 0:
        print_error("Error: found {} missing licenses. These licenses are reported, but missing in the registry".format(len(missing_licenses)))
        for missing_license in missing_licenses:
            print_error("druid_module: {}, groupId: {}, artifactId: {}, version: {}, license: {}".format(missing_license[0], missing_license[1], missing_license[2], missing_license[3], missing_license[4]))
        print_error("")
    
    # Let's find unchecked licenses, which are registered but missing in the report.
    # These licenses should be checked manually.
    for key, registered_license in registered_dep_to_licenses.items():
        if key not in reported_dep_to_licenses:
            unchecked_licenses.append((key[0], key[1], key[2], registered_license))
        elif reported_dep_to_licenses[key][0] == "-":
            unchecked_licenses.append((key[0], key[1], key[2], registered_license))
    
    if len(unchecked_licenses) > 0:
        print_error("Warn: found {} unchecked licenses. These licenses are registered, but not found in dependency reports.".format(len(unchecked_licenses)))
        print_error("These licenses must be checked manually.")
        for unchecked_license in unchecked_licenses:
            print_error("groupId: {}, artifactId: {}, version: {}, reported_license: {}".format(unchecked_license[0], unchecked_license[1], unchecked_license[2], unchecked_license[3]))
    print_error("")

    if len(mismatched_licenses) > 0 or len(missing_licenses) > 0:
        sys.exit(1)


def print_license_name_underbar(license_name):
    underbar = ""
    for _ in range(len(license_name)):
        underbar += "="
    print_outfile("{}\n".format(underbar))


def generate_license(apache_license_v2, license_yaml):
    # Generate LICENSE.BINARY file
    print_error("=== Generating the contents of LICENSE.BINARY file ===\n")
    
    # Print Apache license first.
    print_outfile(apache_license_v2)
    with open(license_yaml) as registry_file:
        licenses_list = list(yaml.load_all(registry_file))

    # Group licenses by license_name, license_category, and then module.
    licenses_map = {}
    for license in licenses_list:
        if license['license_name'] not in licenses_map:
            licenses_map[license['license_name']] = {}
        licenses_of_name = licenses_map[license['license_name']]
        if license['license_category'] not in licenses_of_name:
            licenses_of_name[license['license_category']] = {}
        licenses_of_category = licenses_of_name[license['license_category']]
        if license['module'] not in licenses_of_category:
            licenses_of_category[license['module']] = []
        licenses_of_module = licenses_of_category[license['module']]
        licenses_of_module.append(license)

    for license_name, licenses_of_name in sorted(licenses_map.items()):
        print_outfile(license_name)
        print_license_name_underbar(license_name)
        for license_category, licenses_of_category in licenses_of_name.items():
            for module, licenses in licenses_of_category.items():
                print_outfile("{}/{}".format(license_category.upper(), module_to_upper(module)))
                for license in licenses:
                    print_license(license)
                    print_outfile("")
                print_outfile("")


if __name__ == "__main__":
    try:
        parser = argparse.ArgumentParser(description='Check and generate license file.')
        parser.add_argument('apache_license', metavar='<path to apache license file>', type=str)
        parser.add_argument('license_yaml', metavar='<path to license.yaml>', type=str)
        parser.add_argument('out_path', metavar='<path to output file>', type=str)
        parser.add_argument('--dependency-reports', dest='dependency_reports_root', type=str, default=None, metavar='<root to maven dependency reports>')
        args = parser.parse_args()
        
        with open(args.apache_license) as apache_license_file:
            apache_license_v2 = apache_license_file.read()
        license_yaml = args.license_yaml
        dependency_reports_root = args.dependency_reports_root

        with open(args.out_path, "w") as outfile:
            if dependency_reports_root is not None:
                check_licenses(license_yaml, dependency_reports_root)
            generate_license(apache_license_v2, license_yaml)

    except KeyboardInterrupt:
        print('Interrupted, closing.')
