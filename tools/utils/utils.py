from html.parser import HTMLParser


class AppListHTMLParser(HTMLParser):

    def __init__(self):
        self.in_table = False
        self.in_header = False
        self.in_row = False
        self.in_cell = False
        self.debug = False
        self.row = -1
        self.cell = 0
        self.headers = ['Id']
        self.values = []
        super().__init__()

    def attr_to_dict(self, attrs):
        return {i[0]: i[1] for i in attrs}

    def handle_starttag(self, tag, attrs):
        if self.in_table and self.debug:
            print("Encountered a start tag:", tag, attrs)

        if attrs:
            attrs = self.attr_to_dict(attrs)
            if attrs.get('id') == 'hubitapps-table':
                self.in_table = True
            if attrs.get('data-app-id'):
                self.in_row = True
                self.values.append([attrs.get('data-app-id')])
            if tag == 'th' and self.in_table:
                self.in_header = True
            if tag == 'tr' and self.in_table:
                self.in_row = True
                self.row = self.row + 1
                if self.debug:
                    print("starting row:", self.row)
            if tag == 'td' and self.in_row:
                self.in_cell = True
                self.cell = self.cell + 1
                if self.debug:
                    print("starting cell:", self.cell)

    def handle_endtag(self, tag):
        if tag == 'tbody':
            self.in_table = False
        elif tag == 'td':
            self.in_cell = False
        elif tag == 'th':
            self.in_header = False
        elif tag == 'tr':
            self.in_row = False
            self.in_cell = False
            self.cell = 0
        if self.in_table and self.debug:
            print("Encountered an end tag :", tag)

    def handle_data(self, data):
        if self.in_table and self.debug:
            print("Encountered some data  :", data, self.in_table, self.in_row, self.in_cell, self.in_header)
        if self.in_row and self.in_cell:
            if len(self.values[self.row]) < self.cell + 1:
                self.values[self.row].append(data.strip())
            else:
                self.values[self.row][self.cell] = self.values[self.row][self.cell] + data.strip()
        if self.in_table and self.in_header:
            self.headers.append(data.strip())

